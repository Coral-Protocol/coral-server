package org.coralprotocol.coralserver.models

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.Orchestrate
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val privacyKey: String,
    val agentGraph: AgentGraph?,
)

@JvmInline
@Serializable
value class AgentName(private val name: String)
@JvmInline
@Serializable
value class AgentType(private val type: String)

@Serializable
data class AgentGraph(
    val agents: HashMap<AgentName, GraphAgent>,
    val links: Set<Set<String>>,
)

@Serializable
sealed interface GraphAgent {
    @Serializable
    @SerialName("remote")
    @JvmInline
    value class Remote(val provider: Provider.Remote): GraphAgent
    @Serializable
    @SerialName("local")
    data class Local(val agentType: AgentType): GraphAgent
}

private val logger = KotlinLogging.logger {}

@Serializable
sealed class Provider: Orchestrate {
    @Serializable
    data class Remote(
        val host: String,
        val agentType: String,
        val appId: String,
        val privacyKey: String,
    ) : Provider() {
        override fun spawn(): OrchestratorHandle {
            TODO("request agent from remote server")
        }
    }

    @Serializable
    data class Docker(val container: String) : Provider() {
        override fun spawn(): OrchestratorHandle {
            TODO("Not yet implemented")
        }
    }
    @Serializable
    data class Executable(
        val command: List<String>,
        val environment: HashMap<String, String> = hashMapOf()
    ) : Provider() {
        override fun spawn(): OrchestratorHandle {
            val processBuilder = ProcessBuilder().redirectErrorStream(true)
            val environment = processBuilder.environment()
            for ((key, value) in environment) {
                environment[key] = value
            }
            processBuilder.command(command)

            logger.info{"spawning process..."}
            val process = processBuilder.start()

            thread(isDaemon = true) {
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line -> logger.info { "process: $line" } }
            }

            return object: OrchestratorHandle {
                override suspend fun destroy() {
                    withContext(processContext) {
                        process.destroy()
                        process.waitFor(30, TimeUnit.SECONDS)
                        process.destroyForcibly()
                        logger.info{"Process exited"}
                    }
                }
            }

        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
val processContext = newFixedThreadPoolContext(10, "processContext")


/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)