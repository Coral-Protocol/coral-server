package org.coralprotocol.coralserver.e2e

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Test configuration for a Coral agent in the test environment.
 */
data class TestCoralAgentConfig(
    val name: String,
    val description: String = name,
    val systemPrompt: String = defaultSystemPrompt,
    val modelName: String = "gpt-4o",
    val openAiApiKey: String = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY not set"),
)

/**
 * Wrapper for a koog AIAgent that includes the connection to the Coral server.
 */
class TestCoralAgent(
    val config: TestCoralAgentConfig,
    val agent: AIAgent,
    val conversation: MutableList<Message> = mutableListOf<Message>()
) {
    var mostRecentRunAssistantMessage: Message.Assistant? = null

    /**
     * Send a message to the agent and get a response.
     */
    suspend fun ask(message: String): String {
        logger.info { "[User] -> ${config.name}: $message" }
        mostRecentRunAssistantMessage = null
        conversation.add(Message.User(message))
        // TODO: Use a ChatAgent that takes conversations as input and maps to LLM conversation api
        agent.run(Json.Default.encodeToString(conversation))
        val response = conversation.last() as? Message.Assistant
            ?: error("Expected the last message to be an Assistant message, but got: ${conversation.last()}")
        logger.info { "${config.name} -> [User]: $response" }
        return response.toString()
    }
}

val defaultSystemPrompt = """
You have access to communication tools to interact with other agents.

If there are no other agents, remember to re-list the agents periodically using the list tool.

You should know that the user can't see any messages you send, you are expected to be autonomous and respond to the user only when you have finished working with other agents, using tools specifically for that.

You can emit as many messages as you like before using that tool when you are finished or absolutely need user input. You are on a loop and will see a "user" message every 4 seconds, but it's not really from the user.

Run the wait for mention tool when you are ready to receive a message from another agent. This is the preferred way to wait for messages from other agents.

You'll only see messages from other agents since you last called the wait for mention tool. Remember to call this periodically. Also call this when you're waiting with nothing to do.

Don't try to guess any numbers or facts, only use reliable sources. If you are unsure, ask other agents for help.

If you have been given a simple task by the user, you can use the wait for mention tool once with a short timeout and then return the result to the user in a timely fashion.
""".trimIndent()