package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.StreamType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.toStringValue
import org.coralprotocol.coralserver.agent.runtime.executable.EnvVar
import org.coralprotocol.coralserver.config.AddressConsumer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

private val dockerLogger = KotlinLogging.logger {}

@Serializable
@SerialName("docker")
data class DockerRuntime(
    var image: String,
    val environment: List<EnvVar> = listOf(),
) : Orchestrate {

    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        val agentLogger = KotlinLogging.logger("DockerRuntime:${params.agentName}")
        val sanitisedImageName = sanitizeDockerImageName(image, params.agentId)

        val dockerClient = applicationRuntimeContext.dockerClient
        dockerLogger.info { "Spawning Docker container with image: $sanitisedImageName" }

        dockerClient.pullImageIfNeeded(sanitisedImageName)
        dockerClient.printImageInfo(sanitisedImageName)

        val apiUrl = applicationRuntimeContext.getApiUrl(AddressConsumer.CONTAINER)
        val mcpUrl = applicationRuntimeContext.getMcpUrl(params, AddressConsumer.CONTAINER)

        // todo: escape???
        val resolvedEnvs = params.options.map { (key, value) ->
            "$key=${value.toStringValue()}"
        }

        val allEnvs = resolvedEnvs + getCoralSystemEnvs(
            params = params,
            apiUrl = apiUrl,
            mcpUrl = mcpUrl,
            orchestrationRuntime = "docker"
        ).map { (key, value) -> "$key=$value" }

        val sessionId = when (params) {
            is RuntimeParams.Local -> params.session.id
            is RuntimeParams.Remote -> params.session.id
        }

        try {
            val containerCreation = dockerClient.createContainerCmd(sanitisedImageName)
                .withName(getDockerContainerName(sessionId, params.agentName))
                .withEnv(allEnvs)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStopTimeout(1)
                .withAttachStdin(false) // Stdin makes no sense with orchestration
                .exec()

            dockerClient.startContainerCmd(containerCreation.id).exec()

            // Attach to container streams for output redirection
            val attachCmd = dockerClient.attachContainerCmd(containerCreation.id)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withLogs(true)

            val streamCallback = attachCmd.exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame) {
                    val message = String(frame.payload).trimEnd('\n')
                    when (frame.streamType) {
                        StreamType.STDOUT -> {
                            agentLogger.info { message }
                        }

                        StreamType.STDERR -> {
                            agentLogger.warn { message }
                        }

                        else -> {
                            agentLogger.warn { message }
                        }
                    }
                }
            })

            return object : OrchestratorHandle {
                override suspend fun destroy() {
                    withContext(processContext) {
                        try {
                            streamCallback.close()
                        } catch (e: Exception) {
                            dockerLogger.warn { "Failed to close stream callback: ${e.message}" }
                        }

                        warnOnNotModifiedExceptions { dockerClient.stopContainerCmd(containerCreation.id).exec() }
                        warnOnNotModifiedExceptions {
                            withTimeoutOrNull(30.seconds) {
                                dockerClient.removeContainerCmd(containerCreation.id)
                                    .withRemoveVolumes(true)
                                    .exec()
                                return@withTimeoutOrNull true
                            } ?: let {
                                dockerLogger.warn { "Docker container ${params.agentName} did not stop in time, force removing it" }
                                dockerClient.removeContainerCmd(containerCreation.id)
                                    .withRemoveVolumes(true)
                                    .withForce(true)
                                    .exec()
                            }
                            dockerLogger.info { "Docker container ${params.agentName} stopped and removed" }
                        }
                    }
                }
            }
        }
        catch (e: Exception) {
            dockerLogger.error { "Failed to start Docker container: ${e.message}" }
            throw e
        }
    }
}

private suspend fun warnOnNotModifiedExceptions(block: suspend () -> Unit): Unit {
    try {
        block()
    } catch (e: NotModifiedException) {
        dockerLogger.warn { "Docker operation was not modified: ${e.message}" }
    } catch (e: Exception) {
        throw e
    }
}

private fun String.asDockerContainerName(): String {
    return this.replace(Regex("[^a-zA-Z0-9_]"), "_")
        .take(63) // Network-resolvable name limit
        .trim('_')
}

private fun getDockerContainerName(sessionId: String, agentName: String): String {
    // SessionID is too long for Docker container names, so we use a hash for deduplication.
    val randomSuffix = sessionId.hashCode().toString(16).take(11)
    return "${agentName.take(52)}_$randomSuffix".asDockerContainerName()
}

private fun sanitizeDockerImageName(imageName: String, id: AgentRegistryIdentifier): String {
    if (imageName.contains(":")) {
        if (!imageName.endsWith(":${id.version}")) {
            dockerLogger.warn { "Image $imageName does not match the agent version: ${id.version}" }
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
private fun DockerClient.imageExists(imageName: String): Boolean {
    var name = imageName
    if (!imageName.contains(":")) {
        name = "$name:latest"
    }

    return listImagesCmd().exec().firstOrNull { it.repoTags.contains(name) } != null
}

/**
 * Pulls a Docker image if it doesn't exist locally.
 * @param imageName The name of the image to pull
 */
private fun DockerClient.pullImageIfNeeded(imageName: String) {
    if (!imageExists(imageName)) {
        dockerLogger.info { "Docker image $imageName not found locally, pulling..." }
        val callback = object : ResultCallback.Adapter<PullResponseItem>() {}
        pullImageCmd(imageName).exec(callback)
        callback.awaitCompletion()
        dockerLogger.info { "Docker image $imageName pulled successfully" }
    }
}

/**
 * Checks if the image is using the 'latest' tag and logs a warning if it is.
 * Also includes the image creation date in the warning.
 * @param imageName The name of the image to check
 */
private fun DockerClient.printImageInfo(imageName: String) {
    val imageInfo = inspectImageCmd(imageName).exec()
    val createdAt = imageInfo.created

    if (createdAt != null)  {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val formattedDate = formatter.format(Instant.parse(createdAt))

        dockerLogger.info { "$imageName image creation date: $formattedDate" }
    }
}