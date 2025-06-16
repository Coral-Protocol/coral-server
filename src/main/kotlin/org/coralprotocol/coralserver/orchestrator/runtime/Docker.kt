package org.coralprotocol.coralserver.orchestrator.runtime

import com.github.dockerjava.api.exception.NotModifiedException
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("docker")
data class Docker(val container: String) : AgentRuntime() {
    private val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("unix:///var/run/docker.sock") // TODO: make this configurable
        .build()
    private val dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build()

    override fun spawn(
        agentName: String,
        connectionUrl: String,
        options: Map<String, ConfigValue>
    ): OrchestratorHandle {
        logger.info { "Spawning Docker container: $container" }

        val containerCreation = dockerClient.createContainerCmd(container)
            .withName(agentName)
            .withEnv(options.map { "${it.key}=${it.value}" })
            .exec()

        dockerClient.startContainerCmd(containerCreation.id).exec()

        return object : OrchestratorHandle {
            override suspend fun destroy() {
                withContext(processContext) {
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