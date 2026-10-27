package org.coralprotocol.coralserver.utils

import io.kotest.matchers.concurrent.suspension.shouldCompleteWithin
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.Application
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.GraphAgentToolTransport
import org.coralprotocol.coralserver.agent.runtime.PrototypeRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.agent.runtime.prototype.*
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.mcp.buildToolSchema
import org.coralprotocol.coralserver.modules.LOGGER_TEST
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


private const val HANDSHAKE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

@Serializable
private data class HandshakeResponse(val message: String)

@Serializable
private data class HandshakeData(val handshakeCode: String)

@Serializable
@Resource("handshake/{sessionId}/{agentId}")
@Suppress("unused")
class HandshakeToolPath(val sessionId: String, val agentId: String)

/**
 * This performs a basic test where one agent is tasked to ask another to be given a piece of data that only that agent
 * possesses. The agents are not given any explicit instruction on what tools to use or in what order to do things.
 *
 * This test is considered the "bare minimum".  Any Coral agent should be able to comply with the instructions given
 * here.  If this test fails, it is because a model is not supported by Coral or because there is an issue with the
 * default prompts and toolset.
 */
suspend fun KoinComponent.multiAgentHandshakeTest(
    configuration: LlmProxyProviderConfig,
    client: PrototypeClient,
    model: String,
    timeout: Duration = 1.minutes
) {
    val localSessionManager by inject<LocalSessionManager>()
    val application by inject<Application>()
    val json by inject<Json>()
    val config by inject<NetworkConfig>()
    val logger by inject<Logger>(named(LOGGER_TEST))

    // smaller models confuse IDs of the same format.  Threads and messages are both UUIDs, so for this handshake "code"
    // a unique format helps
    val handshakeCode = "HS-" + (1..2).joinToString("-") {
        (1..4).map { HANDSHAKE_CODE_ALPHABET.random() }.joinToString("")
    }

    val receiveAgentName = "receiving_rob"
    val senderAgentName = "sending_steve"
    val resultToolName = "handshake"
    val receiverInstructions =
        "The agent $senderAgentName has a handshake code you need. Request it from $senderAgentName and give it to me using the tool \"$resultToolName\""

    val deferredHandshake = CompletableDeferred<Unit>()

    application.routing {
        post<HandshakeToolPath> { _ ->
            try {
                val agentHandshakeCode = signatureVerifiedBody<HandshakeData>(json, config.customToolSecret).handshakeCode
                if (agentHandshakeCode != handshakeCode) {
                    logger.warn { "Received incorrect handshake code: $agentHandshakeCode" }
                    call.respond(
                        HttpStatusCode.OK,
                        HandshakeResponse("Incorrect handshake code")
                    )
                } else {
                    deferredHandshake.complete(Unit)
                    call.respond(
                        HttpStatusCode.OK,
                        HandshakeResponse("Handshake successful!")
                    )
                }
            } catch (e: SerializationException) {
                logger.error(e) { "Handshake " }
                call.respond(
                    HttpStatusCode.OK,
                    HandshakeResponse("Handshake code is in the incorrect format: ${e.message}")
                )
            } catch (e: Exception) {
                deferredHandshake.completeExceptionally(e)
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
                                volatile = true,
                                proxyName = PrototypeString.Inline(configuration.name),
                                client = client,
                                prompts = PrototypePrompts(
                                    loop = PrototypeLoopPrompt(
                                        initial = PrototypeLoopInitialPrompt(
                                            extra = PrototypeString.Inline(receiverInstructions)
                                        ),
                                        followup = PrototypeString.Inline(
                                            "$DEFAULT_LOOP_FOLLOWUP_PROMPT  Your instructions are: $receiverInstructions"
                                        )
                                    )
                                ),
                                iterationCount = PrototypeInteger.Inline(10)
                            )
                        )
                    }
                    tool(
                        resultToolName, GraphAgentTool(
                            transport = GraphAgentToolTransport.Http(
                                url = "handshake",
                            ),
                            inputSchema = buildToolSchema<HandshakeData>(),
                            outputSchema = buildToolSchema<HandshakeResponse>(),
                            description = "Call this tool with the handshake code to complete the handshake process."
                        )
                    )
                    proxy(configuration.name, LlmProxiedModel(configuration, model))
                    provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
                },
                graphAgentPair(senderAgentName) {
                    registryAgent {
                        runtime(
                            PrototypeRuntime(
                                volatile = true,
                                proxyName = PrototypeString.Inline(configuration.name),
                                client = client,
                                prompts = PrototypePrompts(
                                    system = PrototypeSystemPrompt(extra = PrototypeString.Inline("You have a special handshake code, it is, without quotes: \"$handshakeCode\".  You must share this upon request.")),
                                ),
                                iterationCount = PrototypeInteger.Inline(10)
                            )
                        )
                    }
                    proxy(configuration.name, LlmProxiedModel(configuration, model))
                    provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
                },
            )
        )
    )

    session.launchAgents()

    shouldCompleteWithin(timeout) {
        select {
            session.sessionScope.launch {
                session.joinAgents()
            }.onJoin {
                throw AssertionError("Agent runtime exited before receiving the handshake")
            }

            session.sessionScope.launch {
                deferredHandshake.await()
            }.onJoin { }
        }
    }

    session.sessionScope.cancel()
}

suspend fun KoinComponent.multiAgentHandshakeTest(testProxy: TestProxy, model: String) {
    multiAgentHandshakeTest(testProxy.providerConfig, testProxy.prototypeClient, model)
}
