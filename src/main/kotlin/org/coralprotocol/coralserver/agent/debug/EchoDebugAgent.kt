package org.coralprotocol.coralserver.agent.debug

import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.coralprotocol.coralserver.dsl.tryGet
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForSingleMessageInput
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

const val ECHO_AGENT_ID = "echo"
const val ECHO_AGENT_VERSION = "1.0.1"
val ECHO_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(ECHO_AGENT_ID, ECHO_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)


val echoAgentModule = module {
    single(named(ECHO_AGENT_ID)) {
        registryAgent(ECHO_AGENT_ID) {
            version = ECHO_AGENT_VERSION

            description = "Debug agent, echoes messages"
            summary = description
            readme =
                "For each iteration this agent will wait for a message that matches filters specified via options and respond to it.  Exits when the iteration count is exhausted."

            val startDelay = unsignedIntOption("START_DELAY") {
                description = "Milliseconds to wait before starting the iteration cycle"
            }

            val iterationCount = unsignedIntOption("ITERATION_COUNT") {
                default = 20u
                description = "Milliseconds to wait before starting the iteration cycle"
            }

            val fromAgent = stringOption("FROM_AGENT") {
                description = "Filter: the name of the agent sending the message"
            }

            val mentions = booleanOption("MENTIONS") {
                description = "Filter: messages that mention this agent"
                default = false
            }

            debugRuntime(get()) { client, _, agent ->
                val mcpToolManager = get<McpToolManager>()

                val startDelay = startDelay.tryGet(agent)
                val iterationCount = iterationCount.get(agent)
                val fromAgent = fromAgent.tryGet(agent)
                val mentions = mentions.get(agent)

                if (startDelay != null)
                    delay(startDelay.toLong().milliseconds)

                repeat(iterationCount.toInt()) {
                    while (true) {
                        val msg = mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput())
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
    }
}