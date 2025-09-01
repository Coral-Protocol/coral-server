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
import org.coralprotocol.coralserver.models.agent.ClaimAgentsModel
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

        // todo: verify
//
//        // todo: check quantity of agent
//
//        // This server must export all the requested agents to be claimed
//        val missingAgents = request.agents.filter { (name, _) ->
//            !registry.exportedAgents.containsKey(name)
//        }
//        if (missingAgents.isNotEmpty()) {
//            throw RouteException(HttpStatusCode.BadRequest,
//                "One or more agents are not exported: ${missingAgents.joinToString()}")
//        }
//
//        val agents = request.agents.map { request ->
//            // This is safe, exported agents must come from exported agents, and an exception is thrown above if any
//            // of the request agents are not exported agents
//            val name = request.type // todo: better naming of this variable and others
//            val agent = registry.importedAgents[request.type]!!
//
//            val missingRequiredOptions = agent.options.filter { option ->
//                option.value.required && !request.options.containsKey(option.key)
//            }
//            if (missingRequiredOptions.isNotEmpty()) {
//                throw RouteException(
//                    HttpStatusCode.BadRequest,
//                    "Agent '${name}' is missing required options: ${missingRequiredOptions.keys.joinToString()}"
//                )
//            }
//
//            val missingAgentOptions = request.options.filter {
//                !agent.options.containsKey(it.key)
//            }
//            if (missingAgentOptions.isNotEmpty()) {
//                throw RouteException(
//                    HttpStatusCode.BadRequest,
//                    "Agent '${name}' contains non-existent options: ${missingRequiredOptions.keys.joinToString()}"
//                )
//            }
//
//            val defaultOptions =
//                agent.options.mapValues { option -> option.value.defaultAsValue() }
//                    .filterNotNullValues()
//
//            GraphAgent(
//                name,
//                blocking = false,
//                extraTools = request.extraTools,
//                systemPrompt = request.systemPrompt,
//                options = defaultOptions + request.options,
//                provider = GraphAgentProvider.Local(request.runtime)
//            )
//        }

        call.respond(HttpStatusCode.OK, remoteSessionManager.createClaim(
            GraphAgent(
                name = request.agentName,
                blocking = request.blocking == true,
                extraTools = request.tools,
                systemPrompt = request.systemPrompt,
                options = mapOf(),
                provider = request.provider
            )
        ))
    }
}