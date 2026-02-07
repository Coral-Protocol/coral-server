package org.coralprotocol.coralserver.agent.runtime

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.logging.LoggingTagIo
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.util.isWindows
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
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
        val potentialPaths = buildList {
            // on Windows, if given a path without an extension, try .exe, .cmd and .bat files
            // on Linux it is expected that a marks files as executables and uses the appropriate shebang to achieve
            // this
            val variations = if (isWindows()) {
                listOf(path, "$path.exe", "$path.cmd", "$path.bat")
            } else {
                listOf(path)
            }

            for (variation in variations) {
                val path = Path.of(variation)

                // specifying an absolute path has the highest priority
                if (path.isAbsolute)
                    add(path)

                // relative to coral-agent.toml comes next
                if (executionContext.path != null)
                    add(executionContext.path.resolve(path))

                // then on PATH
                System.getenv("PATH").split(File.pathSeparator).forEach {
                    add(Path.of(it).resolve(path))
                }
            }
        }

        val existingExecutable = potentialPaths.filter { it.exists() && it.toFile().canExecute() }
        if (existingExecutable.isEmpty()) {
            executionContext.logger.error { "no executables found with given path \"$path\"" }
            return
        }

        val path = if (existingExecutable.size > 1) {
            executionContext.logger.warn { "\"$path\" matches multiple files: \n - ${existingExecutable.joinToString("\n - ")}" }
            existingExecutable.first().absolutePathString()
        } else {
            existingExecutable.first().absolutePathString()
        }

        val argumentString = if (arguments.isNotEmpty()) {
            " with arguments: \"${arguments.joinToString(" ")}\""
        } else {
            ""
        }

        executionContext.logger.info { "Executing \"$path\"$argumentString" }

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
