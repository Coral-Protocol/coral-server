package org.coralprotocol.coralserver.models

import com.sksamuel.hoplite.ConfigAlias
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.orchestrator.AgentOptionValue
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
    val agentGraph: AgentGraphRequest?,
)

@Serializable
data class AgentGraphRequest(
    val agents: HashMap<AgentName, GraphAgentRequest>,
    val links: Set<Set<String>>,
)

@Serializable
sealed interface GraphAgentRequest {
    val options: Map<String, JsonPrimitive>

    @Serializable
    @SerialName("remote")
    data class Remote(val remote: AgentRuntime.Remote, override val options: Map<String, JsonPrimitive> = mapOf()) :
        GraphAgentRequest

    @Serializable
    @SerialName("local")
    data class Local(val agentType: AgentType, override val options: Map<String, JsonPrimitive> = mapOf()) :
        GraphAgentRequest
}

@JvmInline
@Serializable
value class AgentName(private val name: String)

@JvmInline
@Serializable
value class AgentType(private val type: String)


@Serializable
data class AgentGraph(
    val agents: Map<AgentName, GraphAgent>,
    val links: Set<Set<String>>,
)

sealed interface GraphAgent {
    val options: Map<String, AgentOptionValue>

    data class Remote(val remote: AgentRuntime.Remote, override val options: Map<String, AgentOptionValue> = mapOf()) :
        GraphAgent

    data class Local(val agentType: AgentType, override val options: Map<String, AgentOptionValue> = mapOf()) :
        GraphAgent
}

private val logger = KotlinLogging.logger {}

@Serializable
sealed class AgentRuntime : Orchestrate {
    @Serializable
    @SerialName("remote")
    data class Remote(
        val host: String,
        val agentType: String,
        val appId: String,
        val privacyKey: String,
    ) : AgentRuntime() {
        override fun spawn(options: Map<String, AgentOptionValue>): OrchestratorHandle {
            TODO("request agent from remote server")
        }
    }

    @Serializable
    @SerialName("docker")
    data class Docker(val container: String) : AgentRuntime() {
        override fun spawn(options: Map<String, AgentOptionValue>): OrchestratorHandle {
            TODO("Not yet implemented")
        }
    }

    @Serializable
    @SerialName("executable")
    data class Executable(
        val command: List<String>,
        val environment: List<EnvVar> = listOf()
    ) : AgentRuntime() {
        override fun spawn(options: Map<String, AgentOptionValue>): OrchestratorHandle {
            val processBuilder = ProcessBuilder().redirectErrorStream(true)
            val environment = processBuilder.environment()
            environment.clear()
            for (env in this.environment) {
                val (key, value) = env.resolve(options)
                environment[key] = value
            }
            processBuilder.command(command)

            logger.info { "spawning process..." }
            val process = processBuilder.start()

            thread(isDaemon = true) {
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line -> logger.info { "process: $line" } }
            }

            return object : OrchestratorHandle {
                override suspend fun destroy() {
                    withContext(processContext) {
                        process.destroy()
                        process.waitFor(30, TimeUnit.SECONDS)
                        process.destroyForcibly()
                        logger.info { "Process exited" }
                    }
                }
            }

        }
    }
}

@Serializable
data class EnvVar(
    val name: String?,
    val value: String?,
    val from: String?,

    val option: String?,
) {
    // TODO (alan): bake this validation into the type system
    //              EnvVar should be a sum type of 'name/from', 'option' & 'name/value'
    fun validate() {
        if (option != null && (from != null || value != null || name != null)) {
            throw IllegalArgumentException("'option' key is shorthand for 'name' & 'from', it must be used on its own")
        }
        if (name != null && (value == null && from == null)) {
            throw IllegalArgumentException("'value' or 'from' must be provided")
        }
        if (from != null && value != null) {
            throw IllegalArgumentException("'from' and 'value' are mutually exclusive")
        }
        if (name == null && value == null && from == null && option == null) {
            throw IllegalArgumentException("Invalid environment variable definition")
        }
    }

    fun resolve(options: Map<String, AgentOptionValue>): Pair<String, String?> {
        if (option != null) {
            val opt = options[name] ?: throw IllegalArgumentException("Undefined option '$name'")
            return Pair(option, opt.toString())
        }
        val name = name ?: throw IllegalArgumentException("name not provided")
        if(from != null) {
            val opt = options[from] ?: throw IllegalArgumentException("Undefined option '$from'")
            return Pair(from, opt.toString())
        }
        if(value != null) {
            return Pair(name, value)
        }
        throw IllegalArgumentException("Invalid environment variable definition")
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