package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.option
import org.coralprotocol.coralserver.agent.registry.option.asEnvVarValue
import org.coralprotocol.coralserver.agent.registry.option.asFileSystemValue
import org.coralprotocol.coralserver.agent.registry.option.toDisplayString
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.models.SessionAgentState
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.concurrent.thread
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

@Serializable
@SerialName("executable")
data class ExecutableRuntime(
    val command: List<String>
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

        val coralEnvs = getCoralSystemEnvs(
            params = params,
            apiUrl = apiUrl,
            mcpUrl = mcpUrl,
            orchestrationRuntime = "executable"
        )

        // Send options to executable BEFORE setting Coral environment variables.  If a user erroneously created options
        // for their agent that use the same name as Coral envs and used the "env" transport, they should not override
        // real Coral envs ...
        val tempFiles = mutableListOf<Path>()
        params.options.forEach { (name, value) ->
            @Suppress("DuplicatedCode")
            when (value.option().transport) {
                AgentOptionTransport.ENVIRONMENT_VARIABLE -> {
                    processEnvironment[name] = value.asEnvVarValue()
                    logger.info { "Setting option \"$name\" = \"${value.toDisplayString()}\" for agent ${params.agentName}" }
                }
                AgentOptionTransport.FILE_SYSTEM -> {
                    val files = value.asFileSystemValue()
                    val env = files.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
                    processEnvironment[name] = env
                    tempFiles.addAll(files)

                    logger.info { "Setting option \"$name\" = \"$env\" for agent ${params.agentName}" }
                }
            }
        }

        // ... which are set here
        for (env in coralEnvs) {
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

        return object : OrchestratorHandle(tempFiles) {
            override suspend fun cleanup() {
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