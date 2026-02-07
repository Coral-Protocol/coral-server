package org.coralprotocol.coralserver.agent.runtime

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.logging.LoggingTagIo
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import kotlin.io.path.exists

@Serializable
@SerialName("executable")
data class ExecutableRuntime(
    val path: String,
    val arguments: List<String> = listOf(),
    override val transport: AgentRuntimeTransport = DEFAULT_AGENT_RUNTIME_TRANSPORT,
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        val path = if (executionContext.path != null) {
            val relative = executionContext.path.resolve(path)
            if (relative.exists()) {
                relative.toString()
            } else {
                path
            }
        } else {
            path
        }

        executionContext.logger.info { "Executing \"$path\" with arguments: \"${arguments.joinToString(" ")}\"" }

        val result = process(
            command = (listOf(path) + arguments).toTypedArray(),
            directory = executionContext.path?.toFile(),
            stdout = Redirect.Consume {
                it.collect { line -> executionContext.logger.info(LoggingTag.Io(LoggingTagIo.OUT)) { line } }
            },
            stderr = Redirect.Consume {
                it.collect { line -> executionContext.logger.warn(LoggingTag.Io(LoggingTagIo.ERROR)) { line } }
            },
            env = executionContext.buildEnvironment(transport)
        )

        if (result.resultCode != 0) {
            executionContext.logger.warn { "exited with code ${result.resultCode}" }
        } else
            executionContext.logger.info { "exited with code 0" }
    }
}