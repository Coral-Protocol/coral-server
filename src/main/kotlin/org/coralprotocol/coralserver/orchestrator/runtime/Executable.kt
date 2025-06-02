package org.coralprotocol.coralserver.orchestrator.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.OrchestratorHandle
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
    override fun spawn(agentName: String, connectionUrl: String, options: Map<String, ConfigValue>): OrchestratorHandle {
        val processBuilder = ProcessBuilder().redirectErrorStream(true)
        val environment = processBuilder.environment()
        environment.clear()
        for (env in this.environment) {
            val (key, value) = env.resolve(options)
            environment[key] = value
        }
        // TODO: error if someone tries passing 'CORAL_CONNECTION_URL' themselves
        environment["CORAL_CONNECTION_URL"] = connectionUrl
        processBuilder.command(command)

        logger.info { "spawning process..." }
        val process = processBuilder.start()

        // TODO (alan): re-evaluate this when it becomes a bottleneck
        thread(isDaemon = true) {
            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line -> logger.info { "[STDOUT] $agentName: $line" } }
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