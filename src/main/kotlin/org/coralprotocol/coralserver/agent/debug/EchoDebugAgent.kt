package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.buildFullOption
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForSingleMessageInput
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import org.koin.core.component.inject

class EchoDebugAgent(client: HttpClient) : DebugAgent(client) {
    override val companion: DebugAgentIdHolder
        get() = Companion

    companion object : DebugAgentIdHolder {
        override val identifier: RegistryAgentIdentifier
            get() = RegistryAgentIdentifier("echo", "1.0.0", AgentRegistrySourceIdentifier.Local)
    }

    override val options: Map<String, AgentOption>
        get() = mapOf(
            AgentOption.UInt().buildFullOption(
                name = "START_DELAY",
                description = "Milliseconds to wait before starting the iteration cycle",
                required = false
            ),
            AgentOption.UInt(20u).buildFullOption(
                name = "ITERATION_COUNT",
                description = "The number of times to iterate",
                required = false
            ),
            AgentOption.String().buildFullOption(
                name = "FROM_AGENT",
                description = "Filter: the name of the agent sending the message",
                required = false
            ),
            AgentOption.Boolean().buildFullOption(
                name = "MENTIONS",
                description = "Filter: messages that mention this agent",
                required = false
            ),
        )

    override val description: String
        get() = "For each iteration this agent will wait for a message that matches the specified options and respond to it.  Exits when the iteration count is exhausted."

    override val exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>
        get() = genericExportSettings

    private val mcpToolManager by inject<McpToolManager>()

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        val iterationCount = getRequiredOption<AgentOptionValue.UInt>(agent, "ITERATION_COUNT").value
        val fromAgent = getOption<AgentOptionValue.String>(agent, "FROM_AGENT")?.value
        val mentions = getOption<AgentOptionValue.Boolean>(agent, "MENTIONS")?.value == true
        val startDelay = getOption<AgentOptionValue.UInt>(agent, "START_DELAY")

        if (startDelay != null)
            delay(startDelay.value.toLong())

        repeat(iterationCount.toInt()) {
            while (true) {
                val msg = mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput)
                    .message

                if (msg != null && (!mentions || msg.mentionNames.contains(agent.name)) && (fromAgent == null || msg.senderName == fromAgent)) {
                    mcpToolManager.sendMessageTool.executeOn(
                        client,
                        SendMessageInput(msg.threadId, "nice message!", listOf(msg.senderName))
                    )
                    break;
                }
            }
        }
    }
}