package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentExport
import org.coralprotocol.coralserver.agent.registry.PublicRegistryAgent
import org.coralprotocol.coralserver.agent.registry.defaultAsValue
import org.coralprotocol.coralserver.agent.registry.toPublic
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/agents")
class Agents

@Resource("/api/v1/agents/exported")
class ExportedAgents

@Resource("/api/v1/agents/claim")
class ClaimAgents

fun Routing.agentApiRoutes(
    appConfig: ConfigCollection,
    sessionManager: SessionManager,
    remoteSessionManager: RemoteSessionManager
) {
    get<Agents>({
        summary = "Get available agents"
        description = "Fetches a list of all agents available to the Coral server"
        operationId = "getAvailableAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<PublicRegistryAgent>> {
                    description = "List of available agents"
                }
            }
        }
    }) {
        val agents = appConfig.registry.importedAgents.map { entry -> entry.value.toPublic(entry.key) }
        call.respond(HttpStatusCode.OK, agents)
    }

    get<ExportedAgents>({
        summary = "Gets exported agents"
        description = "Fetches agents the Coral server has exported to other servers"
        operationId = "getExportedAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<AgentExport>> {
                    description = "List of exported agents"
                }
            }
        }
    }) {
        val agents = appConfig.registry.exportedAgents.map { entry -> entry.value.toPublic(entry.key) }
        call.respond(HttpStatusCode.OK, agents)
    }

    post<ClaimAgents>({
        summary = "Claim agents"
        description = "Creates a claim for agents that can later be instantiated via WebSocket"
        operationId = "claimAgents"
        request {
            body<GraphAgentRequest> {
                description = "A list of agents to claim"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<String> {
                    description = "Claim ID"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent doesn't exist or is not exported"
            }
            HttpStatusCode.BadRequest to {
                description = "Request contains invalid agents"
            }
        }
    }) {
        val registry = appConfig.registry
        val request = call.receive<GraphAgentRequest>()

        if (request.provider is GraphAgentProvider.Remote) {
            throw RouteException(HttpStatusCode.BadRequest, "Agents with remote providers cannot be claimed")
        }

        val agent = registry.exportedAgents[request.registryAgentName]?.agent
            ?: throw RouteException(HttpStatusCode.NotFound, "Agent '${request.registryAgentName}' is not exported")

        val missingRequiredOptions = agent.options.filter { option ->
            option.value.required && !request.options.containsKey(option.key)
        }
        if (missingRequiredOptions.isNotEmpty()) {
            throw RouteException(
                HttpStatusCode.BadRequest,
                "Agent '${request.registryAgentName}' is missing required options: ${missingRequiredOptions.keys.joinToString()}"
            )
        }

        val missingAgentOptions = request.options.filter {
            !agent.options.containsKey(it.key)
        }
        if (missingAgentOptions.isNotEmpty()) {
            throw RouteException(
                HttpStatusCode.BadRequest,
                "Agent '${request.registryAgentName}' contains non-existent options: ${missingRequiredOptions.keys.joinToString()}"
            )
        }

        val defaultOptions =
            agent.options.mapValues { option -> option.value.defaultAsValue() }
                .filterNotNullValues()

        call.respond(HttpStatusCode.OK, remoteSessionManager.createClaim(
            GraphAgent(
                name = request.registryAgentName,
                blocking = request.blocking == true,
                extraTools = request.tools,
                systemPrompt = request.systemPrompt,
                options = defaultOptions + request.options,
                provider = request.provider
            )
        ))
    }
}