package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.*
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.logging.LoggingTagIo
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

/**
 * The maximum time to wait for the remaining outpu/t of a container after it exits
 */
private const val OUTPUT_DRAIN_TIMEOUT_SECONDS = 5L

@Serializable
@SerialName("docker")
data class DockerRuntime(
    val image: String,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,
    @Optional val command: List<String>? = null
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) = withContext(Dispatchers.IO) {
        if (applicationRuntimeContext.dockerClient == null) {
            executionContext.logger.warn { "Docker client not available, skipping execution" }
            return@withContext
        }

        val docker = applicationRuntimeContext.dockerClient
        val sanitisedImageName =
            sanitizeDockerImageName(image, executionContext.registryAgent.identifier, executionContext.logger)
        var containerId: String? = null

        try {
            runInterruptible {
                var image = docker.findImage(sanitisedImageName)

                if (image == null) {
                    image = docker.pullImage(sanitisedImageName, executionContext.logger)
                        ?: throw IllegalStateException("Docker image $sanitisedImageName not found after pulling")
                }

                docker.printImageInfo(image, executionContext.logger)
            }

            // This call populates executionContext.disposableResources and must be called before the Docker volumes are
            // created
            val environment = executionContext.buildEnvironment(transport)

            val volumes = executionContext.disposableResources
                .filterIsInstance<SessionAgentDisposableResource.TemporaryFile>()
                .map {
                    executionContext.logger.debug { "Binding host file ${it.file} to container path ${it.mountPath}" }
                    Bind(it.file.toString(), Volume(it.mountPath))
                }

            val containerCreationCmd = docker.createContainerCmd(sanitisedImageName)
                .withName(executionContext.agent.secret)
                .withEnv(environment.map { (key, value) -> "$key=$value" })
                .withHostConfig(HostConfig().withBinds(volumes))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStopTimeout(1)
                .withAttachStdin(false) // Stdin makes no sense with orchestration

            if (command != null)
                containerCreationCmd.withCmd(*command.toTypedArray())

            val container = containerCreationCmd.exec()
            containerId = container.id

            executionContext.logger.info { "container $containerId created" }
            executionContext.session.events.emit(SessionEvent.DockerContainerCreated(containerId))

            // The container is attached to before it is started, so that no output is missed and so that attaching
            // cannot race the container exiting and being removed
            val outputCallback = docker.attachContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withLogs(true)
                .exec(ContainerOutputCallback(executionContext.logger))

            outputCallback.use {
                runInterruptible {
                    outputCallback.awaitAttached()
                }

                docker.startContainerCmd(containerId).exec()

                runInterruptible {
                    val status = docker.waitContainerCmd(containerId)
                        .exec(WaitContainerResultCallback())
                        .awaitStatusCode()

                    executionContext.logger.info { "container $containerId exited with code $status" }

                    // the output stream ends shortly after the container exits, the remaining output must be read
                    // before the container is removed
                    if (!outputCallback.awaitCompletion(OUTPUT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        executionContext.logger.warn { "timed out reading the remaining output of container $containerId" }
                }
            }
        } catch (e: DockerClientException) {
            @OptIn(InternalAPI::class)
            if (e.rootCause is InterruptedException)
                throw CancellationException("Docker timeout", e)

            executionContext.logger.error(e) { "Docker error while running container $containerId" }
        } finally {
            withContext(NonCancellable) {
                when (val containerId = containerId) {
                    null -> {}
                    else -> runInterruptible {
                        docker.removeContainerCmd(containerId)
                            .withRemoveVolumes(true)
                            .withForce(true)
                            .exec()

                        executionContext.session.events.tryEmit(SessionEvent.DockerContainerRemoved(containerId))
                        executionContext.logger.info { "container $containerId removed" }
                    }
                }
            }
        }
    }
}

/**
 * Logs the stdout and stderr of a container line by line.  Docker frames are not aligned to lines, a frame can
 * contain multiple lines or part of a line, so frames are buffered per stream until a full line is read.
 */
private class ContainerOutputCallback(private val logger: LoggingInterface) : ResultCallback.Adapter<Frame>() {
    private val buffers = mutableMapOf<StreamType, ByteArrayOutputStream>()

    /**
     * Released when the attach either starts streaming or fails
     */
    private val attached = CountDownLatch(1)

    /**
     * Blocks until the attach is streaming, throwing the attach error if it failed
     */
    fun awaitAttached() {
        attached.await()
        throwFirstError()
    }

    override fun onStart(stream: Closeable?) {
        super.onStart(stream)
        attached.countDown()
    }

    override fun onError(throwable: Throwable?) {
        super.onError(throwable)
        attached.countDown()
    }

    @Synchronized
    override fun onNext(frame: Frame) {
        val buffer = buffers.getOrPut(frame.streamType) { ByteArrayOutputStream() }
        frame.payload.forEach { byte ->
            if (byte == '\n'.code.toByte()) {
                logLine(frame.streamType, buffer)
            } else {
                buffer.write(byte.toInt())
            }
        }
    }

    @Synchronized
    override fun close() {
        // output that did not end with a newline
        buffers.forEach { (streamType, buffer) ->
            if (buffer.size() > 0)
                logLine(streamType, buffer)
        }

        super.close()
    }

    private fun logLine(streamType: StreamType, buffer: ByteArrayOutputStream) {
        val line = buffer.toString(Charsets.UTF_8)
        buffer.reset()

        when (streamType) {
            StreamType.STDOUT -> logger.info(LoggingTag.Io(LoggingTagIo.OUT)) { line }
            StreamType.STDERR -> logger.warn(LoggingTag.Io(LoggingTagIo.ERROR)) { line }
            else -> {}
        }
    }
}

private fun sanitizeDockerImageName(
    imageName: String,
    id: RegistryAgentIdentifier,
    logger: LoggingInterface
): String {
    if (imageName.contains(":")) {
        if (!imageName.endsWith(":${id.version}")) {
            logger.warn { "Image $imageName does not match the agent version: ${id.version}" }
        }

        return imageName
    } else {
        return "$imageName:${id.version}"
    }
}

private fun DockerClient.findImage(imageName: String): Image? =
    listImagesCmd()
        .exec()
        // repoTags is null for untagged images
        .firstOrNull { it.repoTags?.contains(imageName) == true }

private fun DockerClient.pullImage(imageName: String, logger: LoggingInterface): Image? {
    logger.info { "Docker image $imageName not found locally, pulling..." }

    val time = measureTime {
        pullImageCmd(imageName)
            .exec(object : ResultCallback.Adapter<PullResponseItem>() {

            })
            .awaitCompletion()
    }

    logger.info { "Docker image $imageName pulled in $time" }
    return findImage(imageName)
}

private fun DockerClient.printImageInfo(image: Image, logger: LoggingInterface) {
    val imageInfo = inspectImageCmd(image.id).exec()
    val createdAt = imageInfo.created

    if (createdAt != null) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val formattedDate = formatter.format(Instant.parse(createdAt))

        logger.info { "Image tags: ${image.repoTags?.joinToString(", ")}" }
        logger.info { "Image ID: ${image.id}" }
        logger.info { "Image creation date: $formattedDate" }
    }
}
