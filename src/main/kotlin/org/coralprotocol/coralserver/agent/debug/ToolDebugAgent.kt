package org.coralprotocol.coralserver.agent.debug

import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.coralprotocol.coralserver.dsl.tryGet
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

const val TOOL_AGENT_ID = "tool"
const val TOOL_AGENT_VERSION = "1.0.1"
val TOOL_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(TOOL_AGENT_ID, TOOL_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

val toolAgentModule = module {
    single(named(TOOL_AGENT_ID)) {
        registryAgent(TOOL_AGENT_ID) {
            version = TOOL_AGENT_VERSION

            description = "Debug agent that calls a specified tool"
            summary = description
            readme = "After an optional delay, this agent will execute a single tool and then exit"

            val startDelay = unsignedIntOption("START_DELAY") {
                description = "Milliseconds to wait before starting the iteration cycle"
            }

            val toolName = stringOption("TOOL_NAME") {
                description = "The name of the tool to execute"
                required = true
            }

            val toolInput = stringOption("TOOL_INPUT") {
                description = "The input for the tool as a JSON string"
            }

            debugRuntime(get()) { client, _, agent ->
                val json = get<Json>()

                val startDelayValue = startDelay.tryGet(agent)
                val toolNameValue = toolName.get(agent)
                val toolInputValue = toolInput.tryGet(agent) ?: "{}"

                if (startDelayValue != null)
                    delay(startDelayValue.toLong().milliseconds)

                try {
                    val response =
                        client.callTool(
                            CallToolRequest(
                                CallToolRequestParams(
                                    toolNameValue,
                                    json.decodeFromString(toolInputValue)
                                )
                            )
                        )

                    val text = response.content.joinToString("\n") {
                        when (it) {
                            is EmbeddedResource -> it.resource.toString()
                            is AudioContent -> it.data
                            is ImageContent -> it.data
                            is TextContent -> it.text
                            is ResourceLink -> it.toString()
                        }
                    }

                    if (response.isError == true) {
                        agent.logger.warn { "Failed to call tool $toolNameValue: $text" }
                    } else {
                        agent.logger.debug { "Tool $toolNameValue returned: $text" }
                    }
                } catch (e: SerializationException) {
                    agent.logger.error(e) { "Failed to call tool $toolNameValue: bad input" }
                }
            }
        }
    }
}