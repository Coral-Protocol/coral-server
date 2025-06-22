package org.coralprotocol.coralserver.orchestrator.runtime

import com.chrynan.uri.core.Uri
import com.chrynan.uri.core.fromParts
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

private val logger = KotlinLogging.logger(name = "ExecutableRuntime")

@Serializable
@SerialName("executable")
data class Executable(
    val command: List<String>,
    val environment: List<EnvVar> = listOf()
) : AgentRuntime() {
    override fun spawn(
        agentName: String,
        port: UShort,
        relativeMcpServerUri: Uri,
        options: Map<String, ConfigValue>,
        sessionId: String
    ): OrchestratorHandle {
        val processBuilder = ProcessBuilder().redirectErrorStream(true)
        val processEnvironment = processBuilder.environment()
        processEnvironment.clear()
        // TODO: error if someone tries passing coral system envs themselves
        val coralConnectionUrl = Uri.fromParts(
            scheme = "http",
            host = "localhost", // Executables run on the same host as the Coral server
            port = port.toInt(),
            path = relativeMcpServerUri.path,
            query = relativeMcpServerUri.query
        )

        val resolvedOptions = this.environment.associate {
            val (key, value) = it.resolve(options);
            key to (value ?: "")
        }
        val envsToSet = resolvedOptions + getCoralSystemEnvs(coralConnectionUrl, agentName, "executable")
        for (env in envsToSet) {
            processEnvironment[env.key] = env.value
        }

        processBuilder.command(command)

        logger.info { "spawning process..." }
        val process = processBuilder.start()

        // TODO (alan): re-evaluate this when it becomes a bottleneck
        thread(isDaemon = true) {
            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line -> logger.info { "[STDOUT-${sessionId}] $agentName: $line" } }
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

            override var sessionId: String = sessionId
        }

    }
}

@OptIn(DelicateCoroutinesApi::class)
val processContext = newFixedThreadPoolContext(10, "processContext")