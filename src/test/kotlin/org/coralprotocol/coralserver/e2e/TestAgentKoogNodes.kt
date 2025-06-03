package org.coralprotocol.coralserver.e2e

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
private val logger = KotlinLogging.logger {}

// TODO: Contribute better chat remembering to koog
fun AIAgentSubgraphBuilderBase<*, *>.nodeChatLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true,
    conversation: MutableList<Message>
): AIAgentNodeDelegateBase<String, Message.Response> =
    node(name) { conversationSerializedString ->
        // Should use conversation explicitly now, so the subgraph input is ignored
//        val newInputMessage = Json.Default.decodeFromString<List<Message>>(conversationSerializedString)
        val resp: Message.Response = llm.writeSession {
            prompt = prompt.withMessages(conversation)
            if (allowToolCalls) {
                requestLLM()
            } else requestLLMWithoutTools()
        }
        conversation.add(resp)
        resp
    }

// TODO: Contribute better chat remembering to koog
fun AIAgentSubgraphBuilderBase<*, *>.loggedToolCall(
    testCoralAgentConfig: TestCoralAgentConfig,
    name: String? = null,
    conversation: MutableList<Message>,
): AIAgentNodeDelegateBase<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        logger.info { "[${testCoralAgentConfig.name}] Tool call: ${toolCall.tool} with params: ${toolCall.contentJson}" }
        val result = environment.executeTool(toolCall)
        logger.info { "[${testCoralAgentConfig.name}] Tool result: \n$result" }
        // TODO: Prevent double-adding the calls when the previous node already added it
        conversation.add(toolCall)
        result
    }

// TODO: Contribute better chat remembering to koog
fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendToolResult(
    name: String? = null,
    conversation: MutableList<Message>
): AIAgentNodeDelegateBase<ReceivedToolResult, Message.Response> =
    node(name) { result ->
        llm.writeSession {
            // Add tool result to the subgraph prompt
            updatePrompt {
                tool {
                    result(result)
                }
            }
            // Add the tool result to the persistent conversation
            conversation.add(prompt.messages.last())
            val response = requestLLM()
            conversation.add(response)
            response
        }
    }