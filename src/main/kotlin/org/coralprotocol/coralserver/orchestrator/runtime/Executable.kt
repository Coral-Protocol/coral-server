package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.fromParts
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.LogKind
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
import org.coralprotocol.coralserver.orchestrator.RuntimeEvent
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("executable")
data class Executable(
    val command: List<String>,
    val environment: List<EnvVar> = listOf()
) : AgentRuntime() {
    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
    ): OrchestratorHandle {
        val processBuilder = ProcessBuilder()
        val processEnvironment = processBuilder.environment()
        processEnvironment.clear()
        // TODO: error if someone tries passing coral system envs themselves
        val coralConnectionUrl = Uri.fromParts(
            scheme = "http",
            host = "localhost", // Executables run on the same host as the Coral server
            port = params.mcpServerPort.toInt(),
            path = params.mcpServerRelativeUri.path,
            query = params.mcpServerRelativeUri.query
        )

        val resolvedOptions = this.environment.associate {
            val (key, value) = it.resolve(params.options);
            key to (value ?: "")
        }
        val envsToSet = resolvedOptions + getCoralSystemEnvs(params, coralConnectionUrl, "executable")
        for (env in envsToSet) {
            processEnvironment[env.key] = env.value
        }

        processBuilder.command(command)

        logger.info { "spawning process..." }
        val process = processBuilder.start()

        // TODO (alan): re-evaluate this when it becomes a bottleneck
        thread(isDaemon = true) {
            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line ->
                run {
                    bus.emit(RuntimeEvent.Log(kind = LogKind.STDOUT, message = line))
                    logger.info {
                        "[STDOUT] ${params.agentName}: $line"
                    }
                }
            }
        }
        thread(isDaemon = true) {
            val reader = process.errorStream.bufferedReader()
            reader.forEachLine { line ->
                run {
                    bus.emit(RuntimeEvent.Log(kind = LogKind.STDERR, message = line))
                    logger.error {
                        "[STDERR] ${params.agentName}: $line"
                    }
                }
            }
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

@OptIn(DelicateCoroutinesApi::class)
val processContext = newFixedThreadPoolContext(10, "processContext")