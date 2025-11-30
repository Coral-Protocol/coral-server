package org.coralprotocol.coralserver.agent.runtime

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
@SerialName("executable")
data class ExecutableRuntime(
    val command: List<String>
) : AgentRuntime() {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        executionContext.agent.logger.info("Executing command: ${command.joinToString(" ")}")

        val result = process(
            command = command.toTypedArray(),
            directory = executionContext.path.toFile(),
            stdout = Redirect.Consume {
                it.collect { line -> executionContext.agent.logger.info(line) }
            },
            stderr = Redirect.Consume {
                it.collect { line -> executionContext.agent.logger.warn(line) }
            },
            env = applicationRuntimeContext.buildEnvironment(executionContext, AddressConsumer.LOCAL, RuntimeId.EXECUTABLE)
        )

        if (result.resultCode != 0) {
            executionContext.agent.logger.warn("exited with code ${result.resultCode}")
        }
        else
            executionContext.agent.logger.info("exited with code 0")
    }

    /*
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

    }*/
}