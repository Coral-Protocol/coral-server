import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.model.*
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.util.rootCause
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.runtime.AgentRuntime
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.logging.LoggerWithFlow
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
    ) {
        if (applicationRuntimeContext.dockerClient == null) {
            executionContext.logger.warn("Docker client not available, skipping execution")
            return
        }

        val docker = DockerWithLogging(applicationRuntimeContext.dockerClient, executionContext.logger)
        val sanitisedImageName = docker.sanitizeDockerImageName(image, executionContext.registryAgent.info.identifier)

        docker.pullImageIfNeeded(sanitisedImageName)
        docker.printImageInfo(sanitisedImageName)

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

        try {
            docker.dockerClient.startContainerCmd(container.id).exec()

            val attachCmd = docker.dockerClient.attachContainerCmd(container.id)
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
                val status = docker.dockerClient.waitContainerCmd(container.id)
                    .exec(WaitContainerResultCallback())
                    .awaitStatusCode()

                executionContext.logger.info("container ${container.id} exited with code $status")
            }
        }
        catch (e: DockerClientException) {
            @OptIn(InternalAPI::class)
            if (e.rootCause is InterruptedException)
                throw CancellationException("Docker timeout", e)
        }
        finally {
           withContext(NonCancellable) {
               runInterruptible {
                   docker.dockerClient.removeContainerCmd(container.id)
                       .withRemoveVolumes(true)
                       .withForce(true)
                       .exec()

                   executionContext.logger.info("container ${container.id} removed")
               }
            }
        }
    }
}

private fun DockerWithLogging.sanitizeDockerImageName(imageName: String, id: AgentRegistryIdentifier): String {
    if (imageName.contains(":")) {
        if (!imageName.endsWith(":${id.version}")) {
            logger.warn("Image $imageName does not match the agent version: ${id.version}")
        }

        return imageName
    }
    else {
        return "$imageName:${id.version}"
    }
}

/**
 * @param imageName The name of the image to search for
 * @return true if the image is found locally, false otherwise
 */
private fun DockerWithLogging.imageExists(imageName: String): Boolean {
    var name = imageName
    if (!imageName.contains(":")) {
        name = "$name:latest"
    }

    return dockerClient.listImagesCmd().exec().firstOrNull { it.repoTags.contains(name) } != null
}

/**
 * Pulls a Docker image if it doesn't exist locally.
 * @param imageName The name of the image to pull
 */
private fun DockerWithLogging.pullImageIfNeeded(imageName: String) {
    if (!imageExists(imageName)) {
        logger.info("Docker image $imageName not found locally, pulling...")
        val callback = object : ResultCallback.Adapter<PullResponseItem>() {}
        dockerClient.pullImageCmd(imageName).exec(callback)
        callback.awaitCompletion()
        logger.info("Docker image $imageName pulled successfully")
    }
}

/**
 * Checks if the image is using the 'latest' tag and logs a warning if it is.
 * Also includes the image creation date in the warning.
 * @param imageName The name of the image to check
 */
private fun DockerWithLogging.printImageInfo(imageName: String) {
    val imageInfo = dockerClient.inspectImageCmd(imageName).exec()
    val createdAt = imageInfo.created

    if (createdAt != null)  {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val formattedDate = formatter.format(Instant.parse(createdAt))

        logger.info("$imageName image creation date: $formattedDate")
    }
}
