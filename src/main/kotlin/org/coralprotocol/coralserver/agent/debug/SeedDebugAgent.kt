package org.coralprotocol.coralserver.agent.debug

import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.coralprotocol.coralserver.dsl.tryGet
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

const val SEED_AGENT_ID = "seed"
const val SEED_AGENT_VERSION = "1.0.1"
val SEED_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(SEED_AGENT_ID, SEED_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

val seedAgentModule = module {
    single(named(SEED_AGENT_ID)) {
        registryAgent(SEED_AGENT_ID) {
            version = SEED_AGENT_VERSION

            description = "Debug agent that sends messages to a session then exits"
            summary = description
            readme =
                "Seeds a session with a configurable amount of threads and messages.  After all threads and messages were created and sent this agent will exit."

            val startDelay = unsignedIntOption("START_DELAY") {
                description = "Milliseconds to wait before starting the iteration cycle"
            }

            val operationDelay = unsignedIntOption("OPERATION_DELAY") {
                description = "Milliseconds to wait between each operation (creating a thread, sending a message)"
            }

            val seedThreadCount = unsignedIntOption("SEED_THREAD_COUNT") {
                description = "The number of threads to create"
                default = 1u
                // TODO: validation min = 1u
            }

            val seedMessageCount = unsignedIntOption("SEED_MESSAGE_COUNT") {
                description = "The number of messages to send in each created thread"
                default = 0u
            }

            val participants = stringListOption("PARTICIPANTS") {
                description = "A list of participant names to include in each thread"
            }

            val mentions = stringListOption("MENTIONS") {
                description = "A list of agents to mention in each message sent in each thread"
            }

            debugRuntime(get()) { client, _, agent ->
                val mcpToolManager = get<McpToolManager>()

                val startDelayValue = startDelay.tryGet(agent)
                val operationDelayValue = operationDelay.tryGet(agent)

                val seedThreadCountValue = seedThreadCount.get(agent)
                val seedMessageCountValue = seedMessageCount.get(agent)
                val participantsValue = participants.get(agent)
                val mentionsValue = mentions.get(agent)

                if (startDelayValue != null)
                    delay(startDelayValue.toLong().milliseconds)

                repeat(seedThreadCountValue.toInt()) { threadNumber ->
                    val thread = mcpToolManager.createThreadTool.executeOn(
                        client,
                        CreateThreadInput("thread $threadNumber", participantsValue)
                    ).thread

                    if (operationDelayValue != null)
                        delay(operationDelayValue.toLong().milliseconds)

                    repeat(seedMessageCountValue.toInt()) { messageNumber ->
                        mcpToolManager.sendMessageTool.executeOn(
                            client,
                            SendMessageInput(thread.id, "message $messageNumber", mentionsValue)
                        )

                        if (operationDelayValue != null)
                            delay(operationDelayValue.toLong().milliseconds)
                    }
                }
            }
        }
    }
}