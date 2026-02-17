package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.StringAgentOptionValidation
import org.coralprotocol.coralserver.agent.registry.option.buildFullOption
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import kotlin.time.Duration

private enum class EnvironmentFormat {
    JETBRAINS
}

class SocketDebugAgent(client: HttpClient) : DebugAgent(client) {
    override val companion: DebugAgentIdHolder
        get() = Companion

    companion object : DebugAgentIdHolder {
        override val identifier: RegistryAgentIdentifier
            get() = RegistryAgentIdentifier("socket", "1.0.0", AgentRegistrySourceIdentifier.Local)
    }

    override val options: Map<String, AgentOption>
        get() = mapOf(
            AgentOption.String(
                default = EnvironmentFormat.JETBRAINS.name,
                validation = StringAgentOptionValidation(
                    variants = EnvironmentFormat.entries.map { it.name }
                )
            ).buildFullOption(
                name = "ENVIRONMENT_FORMAT",
                description = "The format that the environment variables will be printed to the agent logs in",
                required = false
            ),
        )

    override val description: String
        get() = "This agent provides a 'socket' for another agent runtime to connect to the session with.  This agent will make no MCP connection."

    override val exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>
        get() = genericExportSettings

    override suspend fun runtime(
        executionContext: SessionAgentExecutionContext,
        runtimeContext: ApplicationRuntimeContext
    ) {
        val logger = executionContext.logger
        val format = EnvironmentFormat.valueOf(
            getRequiredOption<AgentOptionValue.String>(
                executionContext.agent,
                "ENVIRONMENT_FORMAT"
            ).value
        )

        // don't include the ENVIRONMENT_FORMAT for this agent, never useful
        val env = executionContext.buildEnvironment().filter { it.key != "ENVIRONMENT_FORMAT" }

        val formatted = when (format) {
            EnvironmentFormat.JETBRAINS -> {
                env.map { (key, value) -> "$key=$value" }.joinToString(";")
            }
        }

        logger.info { "\n\n${formatted}\n\n" }

        // the runtime should not exit by itself
        delay(Duration.INFINITE)
    }

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        // nop, never called
    }
}