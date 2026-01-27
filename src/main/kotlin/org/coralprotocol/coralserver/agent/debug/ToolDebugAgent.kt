package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.buildFullOption
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import org.koin.core.component.inject

class ToolDebugAgent(client: HttpClient) : DebugAgent(client) {
    override val companion: DebugAgentIdHolder
        get() = Companion

    companion object : DebugAgentIdHolder {
        override val identifier: RegistryAgentIdentifier
            get() = RegistryAgentIdentifier("tool", "1.0.0", AgentRegistrySourceIdentifier.Local)
    }

    override val options: Map<String, AgentOption>
        get() = mapOf(
            AgentOption.UInt().buildFullOption(
                name = "START_DELAY",
                description = "Milliseconds to wait before starting the iteration cycle",
                required = false
            ),
            AgentOption.String().buildFullOption(
                name = "TOOL_NAME",
                description = "The name of the tool to execute",
                required = true
            ),
            AgentOption.String().buildFullOption(
                name = "TOOL_INPUT",
                description = "The input for the tool as a JSON string",
                required = false
            ),
        )

    override val description: String
        get() = "After an optional delay, this agent will execute a single tool and then exit"

    override val exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>
        get() = genericExportSettings

    private val json by inject<Json>()

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        val startDelay = getOption<AgentOptionValue.UInt>(agent, "START_DELAY")
        val toolName = getRequiredOption<AgentOptionValue.String>(agent, "TOOL_NAME").value
        val toolInput = getRequiredOption<AgentOptionValue.String>(agent, "TOOL_INPUT").value

        if (startDelay != null)
            delay(startDelay.value.toLong())

        try {
            val response =
                client.callTool(CallToolRequest(toolName, json.decodeFromString(toolInput)))

            if (response == null) {
                agent.logger.warn { "Failed to call tool $toolName: no response" }
                return
            }

            val text = response.content.joinToString("\n") {
                when (it) {
                    is EmbeddedResource -> it.resource.toString()
                    is AudioContent -> it.data
                    is ImageContent -> it.data
                    is TextContent -> it.text ?: "<empty>"
                    is UnknownContent -> "unknown"
                }
            }

            if (response.isError == true) {
                agent.logger.warn { "Failed to call tool $toolName: $text" }
            } else {
                agent.logger.debug { "Tool $toolName returned: $text" }
            }
        } catch (e: SerializationException) {
            agent.logger.error(e) { "Failed to call tool $toolName: bad input" }
        }
    }
}