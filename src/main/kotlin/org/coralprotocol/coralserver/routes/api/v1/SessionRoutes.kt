package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.registry.defaultAsValue
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.CreateSessionRequest
import org.coralprotocol.coralserver.session.CreateSessionResponse
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

@Resource("/api/v1/sessions")
class Sessions

/**
 * Configures session-related routes.
 */
fun Routing.sessionApiRoutes(appConfig: ConfigCollection, sessionManager: SessionManager, devMode: Boolean) {
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
            HttpStatusCode.Forbidden to {
                description = "Invalid application ID or privacy key"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "The agent graph is invalid and could not be processed"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {
        val request = call.receive<CreateSessionRequest>()

        val agentGraph = request.agentGraph?.let { it ->
            val requestedAgents = it.agents
            val registry = appConfig.registry

            val missingAgentLinks = it.links.map { set ->
                set.filter { agent -> !it.agents.containsKey(agent) }
            }.flatten()

            if (missingAgentLinks.isNotEmpty()) {
                throw RouteException(HttpStatusCode.BadRequest,
                    "Links contained agents that are not in the request: ${missingAgentLinks.joinToString()}")
            }

            val missingAgents = it.agents.filter { (_, agent) ->
                !registry.importedAgents.containsKey(agent.registryAgentName)
            }
            if (missingAgents.isNotEmpty()) {
                throw RouteException(HttpStatusCode.BadRequest,
                    "Requested agents not found: ${missingAgentLinks.joinToString()}")
            }

            AgentGraph(
                tools = it.tools,
                links = it.links,
                agents = requestedAgents.mapValues { (agentName, request) ->
                    // The badAgents check above will ensure this is never null
                    //
                    // It'd be more idiomatic to throw the exception here, but the error is nicer when it
                    // contains all the missing agents in the graph, not just the first one
                    val agent = registry.importedAgents[request.registryAgentName]!!

                    val missingRequiredOptions = agent.options.filter { option ->
                        option.value.required && !request.options.containsKey(option.key)
                    }
                    if (missingRequiredOptions.isNotEmpty()) {
                        throw RouteException(
                            HttpStatusCode.BadRequest,
                            "Agent '${agentName}' (${request.registryAgentName}) is missing required options: ${missingRequiredOptions.keys.joinToString()}"
                        )
                    }

                    val missingAgentOptions = request.options.filter {
                        !agent.options.containsKey(it.key)
                    }
                    if (missingAgentOptions.isNotEmpty()) {
                        throw RouteException(
                            HttpStatusCode.BadRequest,
                            "Agent '${agentName}' (${request.registryAgentName}) contains non-existent options: ${missingAgentOptions.keys.joinToString()}"
                        )
                    }

                    val defaultOptions =
                        agent.options.mapValues { option -> option.value.defaultAsValue() }
                            .filterNotNullValues()

                    GraphAgent(
                        request.registryAgentName,
                        blocking = request.blocking ?: true,
                        extraTools = request.tools,
                        systemPrompt = request.systemPrompt,
                        options = defaultOptions + request.options,
                        provider = request.provider
                    )
                }
            )
        }

        // TODO(alan): actually limit agent communicating using AgentGraph.links
        // Create a new session
        val session = when (request.sessionId != null && devMode) {
            true -> {
                try {
                    sessionManager.createSessionWithId(
                        request.sessionId,
                        request.applicationId,
                        request.privacyKey,
                        agentGraph
                    )
                }
                catch (e: Exception) {
                    // TODO: An exception should be made for
                    throw e
                }
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