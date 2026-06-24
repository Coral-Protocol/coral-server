package org.coralprotocol.coralserver.agent.debug

import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration

private enum class EnvironmentFormat {
    JETBRAINS
}

const val SOCKET_AGENT_ID = "socket"
const val SOCKET_AGENT_VERSION = "1.0.1"
val SOCKET_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(SOCKET_AGENT_ID, SOCKET_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

val socketAgentModule = module {
    single(named(SOCKET_AGENT_ID)) {
        registryAgent(SOCKET_AGENT_ID) {
            version = SOCKET_AGENT_VERSION

            description = "Debug agent, controlled by an external application"
            summary = description
            readme =
                "This agent provides a 'socket' for another agent runtime to connect to the session with.  This agent will make no MCP connection."

            val environmentFormat = stringOption("ENVIRONMENT_FORMAT") {
                description = "The format that the environment variables will be printed to the agent logs in"
                default = EnvironmentFormat.JETBRAINS.name
                variants = EnvironmentFormat.entries.map { it.name }
            }

            runtime(FunctionRuntime { executionContext, _ ->
                val format = EnvironmentFormat.valueOf(environmentFormat.get(executionContext.agent))
                val env = executionContext.buildEnvironment().filter { it.key != "ENVIRONMENT_FORMAT" }

                val formatted = when (format) {
                    EnvironmentFormat.JETBRAINS -> {
                        env.map { (key, value) -> "$key=$value" }.joinToString(";")
                        // the runtime should not exit by itself
                        delay(Duration.INFINITE)
                    }
                }

                executionContext.logger.info { "\n\n${formatted}\n\n" }

                // the runtime should not exit by itself
                delay(Duration.INFINITE)
            })
        }
    }
}