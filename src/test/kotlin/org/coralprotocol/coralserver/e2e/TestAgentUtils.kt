package org.coralprotocol.coralserver.e2e

import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.core.credential.KeyCredential
import org.coralprotocol.coralserver.server.CoralServer
import org.eclipse.lmos.arc.agents.Agent
import org.eclipse.lmos.arc.agents.ChatAgent
import org.eclipse.lmos.arc.agents.agents
import org.eclipse.lmos.arc.agents.dsl.AllTools
import org.eclipse.lmos.arc.agents.llm.AIClientConfig
import org.eclipse.lmos.arc.agents.llm.MapChatCompleterProvider
import org.eclipse.lmos.arc.client.azure.AzureAIClient
import org.eclipse.lmos.arc.mcp.McpTools
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


fun createConnectedCoralAgent(
    server: CoralServer,
    namePassedToServer: String,
    descriptionPassedToServer: String,
    systemPrompt: String,
    agentClient: AzureAIClient = createTestAIClient(),
    modelName: String = "gpt-4o",
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ChatAgent = createConnectedCoralAgent(
    "http",
    server.host,
    server.port,
    namePassedToServer,
    descriptionPassedToServer,
    systemPrompt,
    agentClient,
    modelName,
    sessionId,
    privacyKey,
    applicationId
)
/**
 * Creates a connected Coral agent.
 *
 * @param port The port to connect to.
 * @param namePassedToServer The name of the agent passed to the server.
 * @param descriptionPassedToServer The description of the agent passed to the server.
 * @param systemPrompt The system prompt for the agent.
 * @param agentClient The AzureAIClient to use.
 * @param sessionId The session ID for the agent.
 * @param privacyKey The privacy key for the agent.
 * @param applicationId The application ID for the agent.
 * @return The created agent.
 */

fun createConnectedCoralAgent(
    protocol: String = "http",
    host: String = "localhost",
    port: Int,
    namePassedToServer: String,
    descriptionPassedToServer: String,
    systemPrompt: String,
    agentClient: AzureAIClient = createTestAIClient(),
    modelName: String = "gpt-4o",
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ChatAgent = agents(
    functionLoaders = listOf(
        McpTools(
            "$protocol://$host:$port/devmode/$applicationId/$privacyKey/$sessionId/sse?agentId=$namePassedToServer",
            5000.seconds.toJavaDuration()
        )
    ),
    chatCompleterProvider = MapChatCompleterProvider(mapOf(modelName to agentClient)),
) {
    agent {
        this@agent.name = namePassedToServer
        this@agent.description = descriptionPassedToServer

        model {
            modelName
        }

        prompt { systemPrompt }
        tools = AllTools
    }
}.getAgents().first() as ChatAgent

/**
 * Creates an AzureAIClient for testing.
 *
 * @return An AzureAIClient configured for testing.
 */
fun createTestAIClient(): AzureAIClient {
    val config = AIClientConfig(
        modelName = "gpt-4o",
        apiKey = System.getenv("OPENAI_API_KEY"),
        endpoint = "https://api.openai.com/v1",
        client = "?"
    )
    val azureOpenAIClient = OpenAIClientBuilder()
        .endpoint(config.endpoint)
        .credential(KeyCredential(config.apiKey))
        .buildAsyncClient()

    return AzureAIClient(config, azureOpenAIClient)
}

