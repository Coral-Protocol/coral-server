package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.parse
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.StreamType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("docker")
data class Docker(
    val image: String,
    val environment: List<EnvVar> = listOf()
) : AgentRuntime() {
    private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(getDockerSocket())
        .build()
    private val dockerHttpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(dockerClientConfig.dockerHost)
        .sslConfig(dockerClientConfig.sslConfig)
        .maxConnections(1024)
        .build()
    private val dockerClient =
        DockerClientBuilder.getInstance(dockerClientConfig).withDockerHttpClient(dockerHttpClient).build()

    /**
     * Checks if the specified Docker image exists locally.
     * @param imageName The name of the image to check
     * @return true if the image exists locally, false otherwise
     */
    private fun imageExists(imageName: String): Boolean {
        return try {
            dockerClient.inspectImageCmd(imageName).exec()
            true
        } catch (e: NotFoundException) {
            false
        }
    }

    /**
     * Pulls a Docker image if it doesn't exist locally.
     * @param imageName The name of the image to pull
     */
    private fun pullImageIfNeeded(imageName: String) {
        if (!imageExists(imageName)) {
            logger.info { "Docker image $imageName not found locally, pulling..." }
            val callback = object : ResultCallback.Adapter<PullResponseItem>() {}
            dockerClient.pullImageCmd(imageName).exec(callback)
            callback.awaitCompletion()
            logger.info { "Docker image $imageName pulled successfully" }
        }
    }

    /**
     * Checks if the image is using the 'latest' tag and logs a warning if it is.
     * Also includes the image creation date in the warning.
     * @param imageName The name of the image to check
     */
    private fun checkAndWarnForLatestTag(imageName: String) {
        if (imageName.endsWith(":latest") || !imageName.contains(":")) {
            val imageInfo = dockerClient.inspectImageCmd(imageName).exec()
            val createdAt = imageInfo.created

            // Format the date for display
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            val formattedDate = formatter.format(Instant.parse(createdAt))

            logger.warn {
                "Using 'latest' tag for Docker image $imageName is poor practice. " +
                        "Consider using a specific version tag instead. " +
                        "Image creation date: $formattedDate"
            }
        }
    }

    override fun spawn(
        agentName: String,
        port: UShort,
        relativeMcpServerUri: Uri,
        options: Map<String, ConfigValue>
    ): OrchestratorHandle {
        logger.info { "Spawning Docker container with image: $image" }

        // Pull the image if it doesn't exist locally
        pullImageIfNeeded(image)

        // Check if using 'latest' tag and warn if so
        checkAndWarnForLatestTag(image)
        val fullConnectionUrl =
            "http://host.docker.internal:$port/${relativeMcpServerUri.path}${relativeMcpServerUri.query?.let { "?$it" } ?: ""}"

        val resolvedEnvs = this.environment.map {
            val (key, value) = it.resolve(options)
            "$key=$value"
        }
        val allEnvs = resolvedEnvs + getCoralSystemEnvs(
            Uri.parse(fullConnectionUrl),
            agentName,
            "docker"
        ).map { (key, value) -> "$key=$value" }

        val containerCreation = dockerClient.createContainerCmd(image)
            .withName(getDockerContainerName(relativeMcpServerUri, agentName))
            .withEnv(allEnvs)
            .withAttachStdout(true)
            .withAttachStderr(true)
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
                        logger.info { "[STDOUT] $agentName: $message" }
                    }

                    StreamType.STDERR -> {
                        logger.info { "[STDERR] $agentName: $message" }
                    }

                    else -> {
                        logger.warn { "[UNKNOWN] $agentName: $message" }
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
                        logger.warn { "Failed to close stream callback: ${e.message}" }
                    }

                    warnOnNotModifiedExceptions { dockerClient.stopContainerCmd(containerCreation.id).exec() }
                    warnOnNotModifiedExceptions {
                        withTimeoutOrNull(30.seconds) {
                            dockerClient.removeContainerCmd(containerCreation.id)
                                .withRemoveVolumes(true)
                                .exec()
                            return@withTimeoutOrNull true
                        } ?: let {
                            logger.warn { "Docker container $agentName did not stop in time, force removing it" }
                            dockerClient.removeContainerCmd(containerCreation.id)
                                .withRemoveVolumes(true)
                                .withForce(true)
                                .exec()
                        }
                        logger.info { "Docker container $agentName stopped and removed" }
                    }
                }
            }
        }
    }
}

private suspend fun warnOnNotModifiedExceptions(block: suspend () -> Unit): Unit {
    try {
        block()
    } catch (e: NotModifiedException) {
        logger.warn { "Docker operation was not modified: ${e.message}" }
    } catch (e: Exception) {
        throw e
    }
}

private fun String.asDockerContainerName(): String {
    return this.replace(Regex("[^a-zA-Z0-9_]"), "_")
        .take(63) // Network-resolvable name limit
        .trim('_')
}

private fun getDockerContainerName(relativeMcpServerUri: Uri, agentName: String): String {
    // SessionID is too long for Docker container names, so we use a hash of the URI for deduplication.
    val randomSuffix = relativeMcpServerUri.toUriString().hashCode().toString(16).take(11)
    return "${agentName.take(52)}_$randomSuffix".asDockerContainerName()
}

private fun getDockerSocket(): String {
    val specifiedSocket = System.getenv("DOCKER_HOST")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("docker.host")?.takeIf { it.isNotBlank() }
        ?: System.getenv("DOCKER_SOCKET")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("docker.socket")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("CORAL_DOCKER_SOCKET")?.takeIf { it.isNotBlank() }

    if (specifiedSocket != null) {
        return specifiedSocket
    }

    // Use colima socket if it exists
    val homeDir = System.getProperty("user.home") ?: "~"
    val colimaSocket = "$homeDir/.colima/default/docker.sock"
    if (java.io.File(colimaSocket).exists()) {
        return "unix://$colimaSocket"
    }

    // If on windows, use npipe
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        return "npipe:////./pipe/docker_engine"
    }
    return "unix:///var/run/docker.sock" // Default Docker socket location for Unix-like systems
}
