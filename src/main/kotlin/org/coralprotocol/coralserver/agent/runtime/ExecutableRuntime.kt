package org.coralprotocol.coralserver.agent.runtime

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        executionContext.logger.info { "Executing command: ${command.joinToString(" ")}" }

        val result = process(
            command = command.toTypedArray(),
            directory = executionContext.path?.toFile(),
            stdout = Redirect.Consume {
                it.collect { line -> executionContext.logger.info { line } }
            },
            stderr = Redirect.Consume {
                it.collect { line -> executionContext.logger.warn { line } }
            },
            env = executionContext.buildEnvironment()
        )

        if (result.resultCode != 0) {
            executionContext.logger.warn { "exited with code ${result.resultCode}" }
        } else
            executionContext.logger.info { "exited with code 0" }
    }
}