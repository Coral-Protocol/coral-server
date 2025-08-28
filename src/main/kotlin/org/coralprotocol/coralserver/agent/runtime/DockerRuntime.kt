package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.registry.toStringValue
import org.coralprotocol.coralserver.agent.runtime.executable.EnvVar
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("docker")
data class DockerRuntime(
    val image: String,
    val environment: List<EnvVar> = listOf(),
) : Orchestrate {

    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
        sessionManager: SessionManager,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        val dockerClient = applicationRuntimeContext.dockerClient
        logger.info { "Spawning Docker container with image: $image" }

        val apiUrl = applicationRuntimeContext.getApiUrl(AddressConsumer.LOCAL)
        val mcpUrl = applicationRuntimeContext.getMcpUrl(params, AddressConsumer.LOCAL)

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

        val containerCreation = dockerClient.createContainerCmd(image)
            .withName(getDockerContainerName(params.sessionId, params.agentName))
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
                        logger.info { "[STDOUT] ${params.agentName}: $message" }
                    }

                    StreamType.STDERR -> {
                        logger.info { "[STDERR] ${params.agentName}: $message" }
                    }

                    else -> {
                        logger.warn { "[UNKNOWN] ${params.agentName}: $message" }
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
                            logger.warn { "Docker container ${params.agentName} did not stop in time, force removing it" }
                            dockerClient.removeContainerCmd(containerCreation.id)
                                .withRemoveVolumes(true)
                                .withForce(true)
                                .exec()
                        }
                        logger.info { "Docker container ${params.agentName} stopped and removed" }
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

private fun getDockerContainerName(sessionId: String, agentName: String): String {
    // SessionID is too long for Docker container names, so we use a hash for deduplication.
    val randomSuffix = sessionId.hashCode().toString(16).take(11)
    return "${agentName.take(52)}_$randomSuffix".asDockerContainerName()
}