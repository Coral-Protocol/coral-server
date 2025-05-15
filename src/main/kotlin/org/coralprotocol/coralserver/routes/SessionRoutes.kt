package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.models.*
import org.coralprotocol.coralserver.orchestrator.AgentOptionValue
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures session-related routes.
 */
fun Routing.sessionRoutes(sessionManager: SessionManager, devMode: Boolean) {
    // Session creation endpoint

    post("/sessions") {
        try {
            val request = call.receive<CreateSessionRequest>()

            // Validate application and privacy key
            if (!devMode && !AppConfigLoader.isValidApplication(request.applicationId, request.privacyKey)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid application ID or privacy key")
                return@post
            }


            val agentGraph = request.agentGraph?.let {
                val agents = it.agents;
                val registry =
                    sessionManager.orchestrator.registry

                AgentGraph(
                    agents = agents.mapValues { agent ->
                        when (val agentReq = agent.value) {
                            is GraphAgentRequest.Local -> {
                                GraphAgent.Local(
                                    agentType = agentReq.agentType,
                                    options = agentReq.options.mapValues { option ->
                                        val realOption = registry.get(agentReq.agentType).options[option.key]
                                            ?: throw IllegalArgumentException("Unknown option '${option.key}'")
                                        val value = AgentOptionValue.tryFromJson(option.value)
                                            ?: throw IllegalArgumentException("Invalid option type for '${option.key} - expected ${realOption.type}'")
                                        if (value.type != realOption.type) {
                                            throw IllegalArgumentException("Invalid option type for '${option.key}' - expected ${realOption.type}")
                                        }
                                        value
                                    }
                                )
                            }

                            else -> TODO("(alan) remote agent option resolution")
                        }

                    },
                    links = it.links // TODO (alan): link validation
                )
            }

            // Create a new session
            val session = sessionManager.createSession(request.applicationId, request.privacyKey, agentGraph)

            // Return the session details
            call.respond(
                CreateSessionResponse(
                    sessionId = session.id,
                    applicationId = session.applicationId,
                    privacyKey = session.privacyKey
                )
            )

            logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
        } catch (e: Exception) {
            logger.error(e) { "Error creating session" }
            call.respond(HttpStatusCode.InternalServerError, "Error creating session: ${e.message}")
        }
    }
}