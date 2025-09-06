package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.PublicRegistryAgent
import org.coralprotocol.coralserver.agent.registry.defaultAsValue
import org.coralprotocol.coralserver.agent.registry.toPublic
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/agents")
class Agents

@Resource("/api/v1/agents/exported")
class ExportedAgents

@Resource("/api/v1/agents/claim")
class ClaimAgents

fun Routing.agentApiRoutes(
    registry: AgentRegistry,
    localSessionManager: LocalSessionManager,
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
        val agents = registry.agents.map { it.toPublic() }
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
                description = "GraphAgentRequest is invalid in a remote context"
            }
        }
    }) {
        val request = call.receive<GraphAgentRequest>()

        try {
            call.respond(HttpStatusCode.OK, remoteSessionManager.createClaim(request.toGraphAgent(registry, true)))
        }
        catch (e: AgentRequestException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message)
        }
    }
}