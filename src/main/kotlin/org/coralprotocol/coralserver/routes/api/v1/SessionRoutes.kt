package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.*

private val logger = KotlinLogging.logger {}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

@Resource("/api/v1/sessions")
class Sessions

/**
 * Configures session-related routes.
 */
fun Routing.sessionApiRoutes(appConfig: AppConfigLoader, sessionManager: SessionManager, devMode: Boolean) {
    post<Sessions>({
        summary = "Create session"
        description = "Creates a new session"
        operationId = "createSession"
        request {
            body<CreateSessionRequest> {
                description = "Session creation request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<CreateSessionResponse> {
                    description = "Session details"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid application ID or privacy key"
            }
        }
    }) {
        val request = call.receive<CreateSessionRequest>()

        // Validate application and privacy key
        if (!devMode && !appConfig.isValidApplication(request.applicationId, request.privacyKey)) {
            throw RouteException(HttpStatusCode.BadRequest, "Invalid App ID or privacy key")
        }

        val agentGraph = request.agentGraph?.let { it ->
            val agents = it.agents;
            val registry = appConfig.config.registry ?: return@let null

            val unknownAgents =
                it.links.map { set -> set.filter { agent -> !it.agents.containsKey(AgentName(agent)) } }.flatten()
            if (unknownAgents.isNotEmpty()) {
                throw IllegalArgumentException("Unknown agent names in links: ${unknownAgents.joinToString()}")
            }

            AgentGraph(
                tools = it.tools,
                links = it.links,
                agents = agents.mapValues { agent ->
                    when (val agentReq = agent.value) {
                        is GraphAgentRequest.Local -> {
                            val agentDef = registry.get(agentReq.agentType)

                            val missing = agentDef.options.filter { option ->
                                option.value.required && !agentReq.options.containsKey(option.key)
                            }
                            if (missing.isNotEmpty()) {
                                throw IllegalArgumentException("Agent '${agent.key}' Missing required options: ${missing.keys.joinToString()}")
                            }

                            val defaultOptions =
                                agentDef.options.mapValues { option -> option.value.defaultAsValue }
                                    .filterNotNullValues()

                            val setOptions = agentReq.options.mapValues { option ->
                                val realOption = agentDef.options[option.key]
                                    ?: throw IllegalArgumentException("Unknown option '${option.key}'")
                                val value = ConfigValue.tryFromJson(option.value)
                                    ?: throw IllegalArgumentException("Agent '${agent.key}' given invalid type for option '${option.key} - expected ${realOption.type}'")
                                if (value.type != realOption.type) {
                                    throw IllegalArgumentException("Agent '${agent.key}' given invalid type for option '${option.key}' - expected ${realOption.type}")
                                }
                                value
                            }

                            GraphAgent.Local(
                                blocking = agentReq.blocking ?: true,
                                agentType = agentReq.agentType,
                                extraTools = agentReq.tools,
                                systemPrompt = agentReq.systemPrompt,
                                options = defaultOptions + setOptions
                            )
                        }

                        else -> TODO("(alan) remote agent option resolution")
                    }
                }
            )
        }

        // TODO(alan): actually limit agent communicating using AgentGraph.links
        // Create a new session
        val session = when (request.sessionId != null && devMode) {
            true -> {
                sessionManager.createSessionWithId(
                    request.sessionId,
                    request.applicationId,
                    request.privacyKey,
                    agentGraph
                )
            }

            false -> {
                sessionManager.createSession(request.applicationId, request.privacyKey, agentGraph)
            }
        }

        // Return the session details
        call.respond(
            CreateSessionResponse(
                sessionId = session.id,
                applicationId = session.applicationId,
                privacyKey = session.privacyKey
            )
        )

        logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
    }

    // TODO: this should probably be protected (only for debug maybe)
    get<Sessions>({
        summary = "Get sessions"
        description = "Fetches all active session IDs"
        operationId = "getSessions"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<String>> {
                    description = "List of session IDs"
                }
            }
        }
    }) {
        val sessions = sessionManager.getAllSessions()
        call.respond(HttpStatusCode.OK, sessions.map { it.id })
    }
}