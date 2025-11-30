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
}