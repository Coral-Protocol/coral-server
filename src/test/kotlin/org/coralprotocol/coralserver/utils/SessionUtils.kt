@file:OptIn(ExperimentalSerializationApi::class)

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
import kotlinx.serialization.ExperimentalSerializationApi
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
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.mcp.buildToolSchema
import org.coralprotocol.coralserver.modules.LOGGER_TEST
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.MessageId
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
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

@Serializable
private data class MultiAgentTestPayloadResponse(val message: String)

@Serializable
private data class MultiAgentTestPayload(val payloadVerbatim: String)

@Serializable
@Resource("payload/{sessionId}/{agentId}")
@Suppress("unused")
class MultiAgentTestPayloadPath(val sessionId: String, val agentId: String)

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
    val logger by inject<Logger>(named(LOGGER_TEST))
    val payloadData = UUID.randomUUID().toString()

    val receiveAgentName = "rob"
    val senderAgentName = "steve"
    val resultToolName = "post_payload"

    val deferredPayload = CompletableDeferred<String>()

    application.routing {
        post<MultiAgentTestPayloadPath> { _ ->
            try {
                val payload = signatureVerifiedBody<MultiAgentTestPayload>(json, config.customToolSecret).payload
                if (payload != payloadData) {
                    logger.warn { "Received unexpected payload: $payload" }
                    call.respond(
                        HttpStatusCode.OK,
                        MultiAgentTestPayloadResponse("The given payload '$payload' does not match the expected payload, please try again")
                    )
                } else {
                    deferredPayload.complete(payload)
                    call.respond(
                        HttpStatusCode.OK,
                        MultiAgentTestPayloadResponse("Successfully received payload!")
                    )
                }
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
                                                "You require special data, referred to as the 'payload' which $senderAgentName possesses exclusively. Request this data verbatim, then submit it using the $resultToolName, which will check it for an exact match"
                                            )
                                        )
                                    )
                                ),
                                iterationCount = 10,
                                postRequestToLLMCallback = {
                                    // breakpoint here for debugging agent behaviour
                                }
                            )
                        )
                    }
                    tool(
                        resultToolName, GraphAgentTool(
                            transport = GraphAgentToolTransport.Http(
                                url = "payload",
                            ),
                            inputSchema = buildToolSchema<MultiAgentTestPayload>(),
                            outputSchema = buildToolSchema<MultiAgentTestPayloadResponse>()
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
                                    system = PrototypeSystemPrompt(extra = PrototypeString.Inline("You are a helpful agent. You possess unique information: There is a 'Payload' with this value: $payloadData")),
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
