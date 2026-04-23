package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.agent.execution.applyTo
import org.coralprotocol.coralserver.agent.execution.sanitizeImage
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.logging.LoggingTagIo
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

data class DockerContainerSpec(
    val image: String,
    val env: Map<String, String>,
    val entrypoint: List<String>? = null,
    val cmd: List<String>? = null,
    val additionalBinds: List<Bind> = emptyList(),
    val extraCaps: List<Capability> = emptyList(),
)

object DockerLauncher {
    suspend fun launch(
        spec: DockerContainerSpec,
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext,
    ) = withContext(Dispatchers.IO) {
        if (applicationRuntimeContext.dockerClient == null) {
            executionContext.logger.warn { "Docker client not available, skipping execution" }
            return@withContext
        }

        val docker = applicationRuntimeContext.dockerClient
        val sanitisedImageName = executionContext.executionPolicy.docker.sanitizeImage(
            imageName = spec.image,
            id = executionContext.registryAgent.identifier,
            profileName = executionContext.executionPolicy.profileName,
            logger = executionContext.logger,
        )

        val temporaryFileBinds = executionContext.disposableResources
            .filterIsInstance<SessionAgentDisposableResource.TemporaryFile>()
            .map {
                executionContext.logger.debug { "Binding host file ${it.file} to container path ${it.mountPath}" }
                Bind(it.file.toString(), Volume(it.mountPath))
            }
        val allBinds = temporaryFileBinds + spec.additionalBinds

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

            val containerCreationCmd = docker.createContainerCmd(sanitisedImageName)
                .withName(executionContext.agent.secret)
                .withEnv(spec.env.map { (key, value) -> "$key=$value" })
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStopTimeout(1)
                .withAttachStdin(false)

            executionContext.executionPolicy.docker.applyTo(
                cmd = containerCreationCmd,
                volumes = allBinds,
                logger = executionContext.logger,
            )

            if (spec.extraCaps.isNotEmpty()) {
                containerCreationCmd.hostConfig?.withCapAdd(*spec.extraCaps.toTypedArray())
            }

            spec.entrypoint?.let { containerCreationCmd.withEntrypoint(it) }
            spec.cmd?.let { containerCreationCmd.withCmd(*it.toTypedArray()) }

            val container = containerCreationCmd.exec()
            containerId = container.id

            executionContext.logger.info { "container $containerId created" }
            executionContext.session.events.emit(SessionEvent.DockerContainerCreated(containerId))

            docker.startContainerCmd(containerId).exec()

            val attachCmd = docker.attachContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withLogs(true)

            attachCmd.exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    val message = String(frame.payload).trimEnd('\n')
                    if (frame.streamType == StreamType.STDOUT)
                        executionContext.logger.info(LoggingTag.Io(LoggingTagIo.OUT)) { message }

                    if (frame.streamType == StreamType.STDERR)
                        executionContext.logger.warn(LoggingTag.Io(LoggingTagIo.ERROR)) { message }
                }
            })

            runInterruptible {
                val status = docker.waitContainerCmd(containerId)
                    .exec(WaitContainerResultCallback())
                    .awaitStatusCode()

                executionContext.logger.info { "container $containerId exited with code $status" }
            }
        } catch (e: DockerClientException) {
            @OptIn(InternalAPI::class)
            if (e.rootCause is InterruptedException)
                throw CancellationException("Docker timeout", e)

            executionContext.logger.error(e) {
                "Docker client error for agent ${executionContext.agent.name} (image=$sanitisedImageName, container=${containerId ?: "none"})"
            }
            throw e
        } finally {
            withContext(NonCancellable) {
                when (val id = containerId) {
                    null -> {}
                    else -> runInterruptible {
                        docker.removeContainerCmd(id)
                            .withRemoveVolumes(true)
                            .withForce(true)
                            .exec()

                        executionContext.session.events.tryEmit(SessionEvent.DockerContainerRemoved(id))
                        executionContext.logger.info { "container $id removed" }
                    }
                }
            }
        }
    }
}

private fun DockerClient.findImage(imageName: String): Image? =
    listImagesCmd()
        .exec()
        .firstOrNull { it.repoTags.contains(imageName) }

private fun DockerClient.pullImage(imageName: String, logger: LoggingInterface): Image? {
    logger.info { "Docker image $imageName not found locally, pulling..." }

    val time = measureTime {
        pullImageCmd(imageName)
            .exec(object : ResultCallback.Adapter<PullResponseItem>() {})
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
