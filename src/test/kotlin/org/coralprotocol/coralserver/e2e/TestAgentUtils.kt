package org.coralprotocol.coralserver.e2e

import ai.koog.agents.core.agent.ActAIAgent
import ai.koog.agents.core.agent.actAIAgent
import ai.koog.agents.core.agent.requestLLM
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.uuid.ExperimentalUuidApi

/**
 * Creates a Coral session with connected Koog agents.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun createSessionWithConnectedAgents(
    server: CoralServer,
    sessionId: String,
    privacyKey: String,
    applicationId: String,
    noAgentsOptional: Boolean = true,
    agentsInSessionBlock: SessionCoralAgentDefinitionContext.() -> Unit,
): CoralAgentGraphSession {
    val session = server.sessionManager.getOrCreateSession(
        sessionId = sessionId,
        applicationId = applicationId,
        privacyKey = privacyKey,
        agentGraph = null,
    )

    val context = BasicSessionCoralAgentDefinitionContext(server)
    agentsInSessionBlock(context)
    if (noAgentsOptional) {
        session.devRequiredAgentStartCount = context.buildKoogAgents(session).size
    }
    context.buildKoogAgents(session)
    context.onAgentsCreated(SessionCoralAgentDefinitionContext.AgentsCreatedContext())
    return session
}

interface SessionCoralAgentDefinitionContext {
    val server: CoralServer

    @OptIn(ExperimentalUuidApi::class)
    fun agent(
        name: String,
        description: String = name,
        systemPrompt: String = defaultSystemPrompt,
        modelName: LLModel = OpenAIModels.Chat.GPT4o
    ): Deferred<ActAIAgent<Nothing?, Nothing?>>

    class AgentsCreatedContext {
        @OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
        suspend fun Deferred<ActAIAgent<Nothing?, Nothing?>>.getConnected() = this.getCompleted()
    }

    var onAgentsCreated: suspend AgentsCreatedContext.() -> Unit
}

private class BasicSessionCoralAgentDefinitionContext(
    override val server: CoralServer
) : SessionCoralAgentDefinitionContext {

    override var onAgentsCreated: suspend SessionCoralAgentDefinitionContext.AgentsCreatedContext.() -> Unit = { }

    @OptIn(ExperimentalUuidApi::class)
    private val agentsToAdd = mutableListOf<suspend (CoralAgentGraphSession) -> ActAIAgent<Nothing?, Nothing?>>()

    @OptIn(ExperimentalUuidApi::class)
    override fun agent(
        name: String,
        description: String,
        systemPrompt: String,
        modelName: LLModel
    ): Deferred<ActAIAgent<Nothing?, Nothing?>> {
        val deferrable = CompletableDeferred<ActAIAgent<Nothing?, Nothing?>>()
        agentsToAdd.add { session ->
            val connected = createConnectedKoogAgent(
                server = server,
                namePassedToServer = name,
                descriptionPassedToServer = description,
                systemPrompt = systemPrompt,
                modelName = modelName,
                sessionId = session.id,
                privacyKey = session.privacyKey,
                applicationId = session.applicationId
            )
            deferrable.complete(connected)
            return@add connected
        }
        return deferrable
    }

    val context = newFixedThreadPoolContext(5, "E2EResourceTest")

    @OptIn(ExperimentalUuidApi::class)
    suspend fun buildKoogAgents(session: CoralAgentGraphSession): List<ActAIAgent<Nothing?, Nothing?>> =
        coroutineScope {
            return@coroutineScope agentsToAdd.map {
                async(context) { it(session) }
            }.awaitAll()
        }
}

/**
 * Creates a connected Koog agent.
 */



class TestCoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "InlineTestCoralServer")

    @OptIn(DelicateCoroutinesApi::class)
    fun setup() {
        server?.stop()
        server = CoralServer(
            host = host,
            port = port,
            devmode = devmode,
            sessionManager = sessionManager,
            appConfig = ConfigCollection(null)
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
    }
}

private val defaultSystemPrompt = """
You have access to communication tools to interact with other agents.

You can emit as many messages as you like before finishing with other agents.

Don't try to guess facts; ask other agents or use resources.

If given a simple task, wait briefly for mentions and then return the result.
""".trimIndent()
