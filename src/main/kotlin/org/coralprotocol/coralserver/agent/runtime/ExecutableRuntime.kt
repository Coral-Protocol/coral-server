package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.registry.toStringValue
import org.coralprotocol.coralserver.agent.runtime.executable.EnvVar
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.models.SessionAgentState
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("executable")
data class ExecutableRuntime(
    val command: List<String>,
    val environment: List<EnvVar> = listOf()
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        bus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        val agentLogger = KotlinLogging.logger("ExecutableRuntime:${params.agentName}")

        val processBuilder = ProcessBuilder()
        processBuilder.directory(params.path.toFile())
        val processEnvironment = processBuilder.environment()

        val apiUrl = applicationRuntimeContext.getApiUrl(AddressConsumer.LOCAL)
        val mcpUrl = applicationRuntimeContext.getMcpUrl(params, AddressConsumer.LOCAL)

        val resolvedOptions = params.options.mapValues { it.value.toStringValue() }
        val envsToSet = resolvedOptions + getCoralSystemEnvs(
            params = params,
            apiUrl = apiUrl,
            mcpUrl = mcpUrl,
            orchestrationRuntime = "executable"
        )
        
        for (env in envsToSet) {
            processEnvironment[env.key] = env.value
        }

        processBuilder.command(command)

        logger.info { "spawning process..." }
        val process = processBuilder.start()

        // TODO (alan): re-evaluate this when it becomes a bottleneck

        thread(isDaemon = true) {
            process.waitFor()
            bus.emit(RuntimeEvent.Stopped())
            logger.warn {"Process exited for Agent ${params.agentName}"};

            when (params) {
                is RuntimeParams.Local -> params.session.setAgentState(params.agentName, SessionAgentState.Dead)
                is RuntimeParams.Remote -> {
                    // we don't have the responsibility of marking remote agennt's states
                }
            }
        }

        thread(isDaemon = true) {
            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line ->
                run {
                    bus.emit(RuntimeEvent.Log(kind = LogKind.STDOUT, message = line))
                    agentLogger.info { line }
                }
            }
        }
        thread(isDaemon = true) {
            val reader = process.errorStream.bufferedReader()
            reader.forEachLine { line ->
                run {
                    bus.emit(RuntimeEvent.Log(kind = LogKind.STDERR, message = line))
                    agentLogger.warn { line }
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