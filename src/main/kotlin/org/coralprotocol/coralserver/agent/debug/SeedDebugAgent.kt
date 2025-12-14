package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettingsMap
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.UIntAgentOptionValidation
import org.coralprotocol.coralserver.agent.registry.option.buildFullOption
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent

class SeedDebugAgent(client: HttpClient) : DebugAgent(client, "seed") {
    override val options: Map<String, AgentOption>
        get() = mapOf(
            AgentOption.UInt().buildFullOption(
                name = "START_DELAY",
                description = "Milliseconds to wait before starting the iteration cycle",
                required = false
            ),
            AgentOption.UInt().buildFullOption(
                name = "OPERATION_DELAY",
                description = "Milliseconds to wait between each operation (creating a thread, sending a message)",
                required = false
            ),
            AgentOption.UInt(1u, UIntAgentOptionValidation(min = 1u, max = null, variants = null)).buildFullOption(
                name = "SEED_THREAD_COUNT",
                description = "The number of threads to create",
                required = false
            ),
            AgentOption.UInt(0u).buildFullOption(
                name = "SEED_MESSAGE_COUNT",
                description = "The number of messages to send in each created thread",
                required = false
            ),
            AgentOption.StringList().buildFullOption(
                name = "PARTICIPANTS",
                description = "A list of participant names to include in each thread",
                required = false
            ),
            AgentOption.StringList().buildFullOption(
                name = "MENTIONS",
                description = "A list of agents to mention in each message sent in each thread",
                required = false
            ),
        )

    override val description: String
        get() = "For each iteration this agent will wait for a message that matches the specified options and respond to it.  Exits when the iteration count is exhausted."

    override val exportSettings: UnresolvedAgentExportSettingsMap
        get() = genericExportSettings

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        val startDelay = getOption<AgentOptionValue.UInt>(agent, "START_DELAY")?.value
        val operationDelay = getOption<AgentOptionValue.UInt>(agent, "OPERATION_DELAY")?.value

        val seedThreadCount = getRequiredOption<AgentOptionValue.UInt>(agent, "SEED_THREAD_COUNT").value
        val seedMessageCount = getRequiredOption<AgentOptionValue.UInt>(agent, "SEED_MESSAGE_COUNT").value
        val participants = getRequiredOption<AgentOptionValue.StringList>(agent, "PARTICIPANTS").value
        val mentions = getRequiredOption<AgentOptionValue.StringList>(agent, "MENTIONS").value

        if (startDelay != null)
            delay(startDelay.toLong())

        repeat(seedThreadCount.toInt()) { threadNumber ->
            val thread = agent.mcpToolManager.createThreadTool.executeOn(
                client,
                CreateThreadInput("thread $threadNumber", participants.toSet())
            ).thread

            if (operationDelay != null)
                delay(operationDelay.toLong())

            repeat(seedMessageCount.toInt()) { messageNumber ->
                agent.mcpToolManager.sendMessageTool.executeOn(
                    client,
                    SendMessageInput(thread.id, "message $messageNumber", mentions.toSet())
                )

                if (operationDelay != null)
                    delay(operationDelay.toLong())
            }
        }
    }
}