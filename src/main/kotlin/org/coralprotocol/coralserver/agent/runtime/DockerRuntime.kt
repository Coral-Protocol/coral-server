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
import org.coralprotocol.coralserver.logging.LoggerWithFlow
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

private data class DockerWithLogging(
    val dockerClient: DockerClient,
    val logger: LoggerWithFlow
)

@Serializable
@SerialName("docker")
data class DockerRuntime(
    val image: String,
    @Optional val command: List<String>? = null
) : AgentRuntime() {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) = withContext(Dispatchers.IO) {
        if (applicationRuntimeContext.dockerClient == null) {
            executionContext.logger.warn("Docker client not available, skipping execution")
            return@withContext
        }

        val docker = DockerWithLogging(applicationRuntimeContext.dockerClient, executionContext.logger)
        val sanitisedImageName = docker.sanitizeDockerImageName(image, executionContext.registryAgent.identifier)
        var containerId: String? = null

        try {
            runInterruptible {
                var image = docker.findImage(sanitisedImageName)

                if (image == null) {
                    image = docker.pullImage(sanitisedImageName)
                        ?: throw IllegalStateException("Docker image $sanitisedImageName not found after pulling")
                }

                docker.printImageInfo(image)
            }

            // This call populates executionContext.disposableResources and must be called before the Docker volumes are
            // created
            val environment = executionContext.buildEnvironment()

            val volumes = executionContext.disposableResources
                .filterIsInstance<SessionAgentDisposableResource.TemporaryFile>()
                .map {
                    executionContext.logger.info("Binding host file ${it.file} to container path ${it.mountPath}")
                    Bind(it.file.toString(), Volume(it.mountPath))
                }

            val containerCreationCmd = docker.dockerClient.createContainerCmd(sanitisedImageName)
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

            executionContext.logger.info("container $containerId created")
            executionContext.session.events.tryEmit(SessionEvent.DockerContainerCreated(containerId))

            docker.dockerClient.startContainerCmd(containerId).exec()

            val attachCmd = docker.dockerClient.attachContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withLogs(true)

            attachCmd.exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    val message = String(frame.payload).trimEnd('\n')
                    if (frame.streamType == StreamType.STDOUT)
                        executionContext.logger.info(message)

                    if (frame.streamType == StreamType.STDERR)
                        executionContext.logger.warn(message)
                }
            })

            runInterruptible {
                val status = docker.dockerClient.waitContainerCmd(containerId)
                    .exec(WaitContainerResultCallback())
                    .awaitStatusCode()

                executionContext.logger.info("container $containerId exited with code $status")
            }
        } catch (e: DockerClientException) {
            @OptIn(InternalAPI::class)
            if (e.rootCause is InterruptedException)
                throw CancellationException("Docker timeout", e)
        } finally {
            withContext(NonCancellable) {
                when (val containerId = containerId) {
                    null -> {}
                    else -> runInterruptible {
                        docker.dockerClient.removeContainerCmd(containerId)
                            .withRemoveVolumes(true)
                            .withForce(true)
                            .exec()

                        executionContext.session.events.tryEmit(SessionEvent.DockerContainerRemoved(containerId))
                        executionContext.logger.info("container $containerId removed")
                    }
                }
            }
        }
    }
}

private fun DockerWithLogging.sanitizeDockerImageName(imageName: String, id: RegistryAgentIdentifier): String {
    if (imageName.contains(":")) {
        if (!imageName.endsWith(":${id.version}")) {
            logger.warn("Image $imageName does not match the agent version: ${id.version}")
        }

        return imageName
    } else {
        return "$imageName:${id.version}"
    }
}

private fun DockerWithLogging.findImage(imageName: String): Image? =
    dockerClient.listImagesCmd()
        .exec()
        .firstOrNull { it.repoTags.contains(imageName) }

private fun DockerWithLogging.pullImage(imageName: String): Image? {
    logger.info("Docker image $imageName not found locally, pulling...")

    val time = measureTime {
        dockerClient.pullImageCmd(imageName)
            .exec(object : ResultCallback.Adapter<PullResponseItem>() {

            })
            .awaitCompletion()
    }

    logger.info("Docker image $imageName pulled in $time")
    return findImage(imageName)
}

private fun DockerWithLogging.printImageInfo(image: Image) {
    val imageInfo = dockerClient.inspectImageCmd(image.id).exec()
    val createdAt = imageInfo.created

    if (createdAt != null) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val formattedDate = formatter.format(Instant.parse(createdAt))

        logger.info("Image tags: ${image.repoTags?.joinToString(", ")}")
        logger.info("Image ID: ${image.id}")
        logger.info("Image creation date: $formattedDate")
    }
}
