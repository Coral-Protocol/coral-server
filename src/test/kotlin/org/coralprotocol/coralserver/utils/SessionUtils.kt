package org.coralprotocol.coralserver.utils

import io.kotest.matchers.concurrent.suspension.shouldCompleteWithin
import io.kotest.matchers.equals.shouldBeEqual
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.Application
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.GraphAgentToolTransport
import org.coralprotocol.coralserver.agent.runtime.PrototypeRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.agent.runtime.prototype.*
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.mcp.buildToolSchema
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.MessageId
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.time.Duration.Companion.minutes

suspend fun SessionAgent.synchronizedMessageTransaction(sendMessageFn: suspend () -> MessageId) {
    // waiters are removed from this list before they are completed
    val waiter = waiters.first { it.isNotEmpty() }.first()

    val msgId = sendMessageFn()
    val returnedMsg = waiter.deferred.await()

    if (returnedMsg.id != msgId)
        throw IllegalStateException("$name's active waiter returned message ${returnedMsg.id} instead of expected $msgId")
}

/**
 * This performs a basic test where one agent is tasked to ask another to be given a piece of data that only that agent
 * possesses. The agents are not given any explicit instruction on what tools to use or in what order to do things.
 *
 * This test is considered the "bare minimum".  Any Coral agent should be able to comply with the instructions given
 * here.  If this test fails, it is because a model is not supported by Coral or because there is an issue with the
 * default prompts and toolset.
 */
suspend fun KoinComponent.multiAgentPayloadTest(modelProvider: PrototypeModelProvider) {
    val localSessionManager by inject<LocalSessionManager>()
    val application by inject<Application>()
    val json by inject<Json>()
    val config by inject<NetworkConfig>()
    val payloadData = UUID.randomUUID().toString()

    val receiveAgentName = "rob"
    val senderAgentName = "steve"
    val resultToolName = "post_result"

    @Serializable
    class Payload(val payload: String)

    @Serializable
    @Resource("post-result/{sessionId}/{agentId}")
    @Suppress("unused")
    class PostResultPath(val sessionId: String, val agentId: String)

    val deferredPayload = CompletableDeferred<String>()

    application.routing {
        post<PostResultPath> { _ ->
            try {
                deferredPayload.complete(signatureVerifiedBody<Payload>(json, config.customToolSecret).payload)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                deferredPayload.completeExceptionally(e)
                throw e
            }
        }
    }

    val (session, _) = localSessionManager.createSession(
        "test", AgentGraph(
            groups = setOf(setOf(receiveAgentName, senderAgentName)),
            agents = mapOf(
                graphAgentPair(receiveAgentName) {
                    registryAgent {
                        runtime(
                            PrototypeRuntime(
                                modelProvider,
                                prompts = PrototypePrompts(
                                    loop = PrototypeLoopPrompt(
                                        initial = PrototypeLoopInitialPrompt(
                                            extra = PrototypeString.Inline(
                                                "You require special data, named 'payload' which $senderAgentName possesses exclusively.  Request this data immediately, then post it to me using the $resultToolName tool."
                                            )
                                        )
                                    )
                                ),
                                iterationCount = 10
                            )
                        )
                    }
                    tool(
                        resultToolName, GraphAgentTool(
                            transport = GraphAgentToolTransport.Http(
                                url = "post-result",
                            ),
                            inputSchema = buildToolSchema<Payload>()
                        )
                    )
                    provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
                },
                graphAgentPair(senderAgentName) {
                    registryAgent {
                        runtime(
                            PrototypeRuntime(
                                modelProvider,
                                prompts = PrototypePrompts(
                                    system = PrototypeSystemPrompt(extra = PrototypeString.Inline("Payload: $payloadData")),
                                ),
                                iterationCount = 10
                            )
                        )
                    }
                    provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
                },
            )
        )
    )

    session.launchAgents()

    shouldCompleteWithin(1.minutes) {
        deferredPayload.await().shouldBeEqual(payloadData)
    }

    session.cancelAndJoinAgents()
    session.sessionScope.cancel()
}