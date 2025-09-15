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
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.payment.blockchain.BlockchainService

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/agents")
class Agents() {
    @Resource("claim")
    class Claim(val parent: Agents = Agents())

    @Resource("exported/{name}/{version}")
    class ExportedAgent(val parent: Agents = Agents(), val name: String, val version: String)
}

fun Routing.agentApiRoutes(
    registry: AgentRegistry,
    blockchainService: BlockchainService?,
    remoteSessionManager: RemoteSessionManager?,
    jupiterService: JupiterService
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

    post<Agents.Claim>({
        summary = "Claim agents"
        description = "Creates a claim for agents that can later be instantiated via WebSocket"
        operationId = "claimAgents"
        request {
            body<PaidGraphAgentRequest> {
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
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {
        if (remoteSessionManager == null || blockchainService == null)
            throw RouteException(HttpStatusCode.InternalServerError, "Remote agents are disabled")

        val paidGraphAgentRequest = call.receive<PaidGraphAgentRequest>()

        try {
            val claimId =
                checkPaymentAndCreateClaim(
                    request = paidGraphAgentRequest,
                    registry = registry,
                    blockchainService = blockchainService,
                    remoteSessionManager = remoteSessionManager,
                    jupiterService = jupiterService
                )
            call.respond(
                HttpStatusCode.OK,
                claimId
            )
        } catch (e: AgentRequestException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message)
        }
    }

    get<Agents.ExportedAgent>({
        summary = "Get exported agent info"
        description = "Returns export information for a specific agent"
        operationId = "getExportedAgent"
        request {
            pathParameter<String>("name") {
                description = "The name of the exported agent"
            }
            pathParameter<String>("version") {
                description = "The version of the exported agent"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<PublicAgentExportSettingsMap> {
                    description = "Agent settings map, keyed by runtime"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent was not found or is not exported"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {
        val agent = registry.findAgent(AgentRegistryIdentifier(it.name, it.version))
            ?: throw RouteException(HttpStatusCode.NotFound, "Agent with ${it.name}:${it.version} not found")

        call.respond(agent.exportSettings.toPublic())
    }
}

suspend fun checkPaymentAndCreateClaim(
    request: PaidGraphAgentRequest,
    registry: AgentRegistry,
    blockchainService: BlockchainService,
    remoteSessionManager: RemoteSessionManager,
    jupiterService: JupiterService
): String {
    // TODO: Ensure that the session funder is the one claiming
    val escrowSession = blockchainService.getEscrowSession(request.paidSessionId).getOrThrow()

//    val matchingPaidAgentSessionEntry = escrowSession?.agents?.find {
//        it.id == request.graphAgentRequest.name
//    } ?: throw AgentRequestException.SessionNotFundedException("No matching agent in paid session")

    val provider = request.graphAgentRequest.provider as GraphAgentProvider.Local
    val registryAgent = registry.findAgent(id = request.graphAgentRequest.id)
        ?: throw AgentRequestException.SessionNotFundedException("No matching agent in registry")

    val associatedExportSettings = registryAgent.exportSettings[provider.runtime]
        ?: throw AgentRequestException.SessionNotFundedException("Requested runtime is not exported by agent")

    val pricing = associatedExportSettings.pricing

//    if (matchingPaidAgentSessionEntry.cap !in pricing.minPrice..pricing.maxPrice) {
//        throw AgentRequestException.SessionNotFundedException("Paid session agent cap ${matchingPaidAgentSessionEntry.cap} is not within the pricing range ${pricing.minPrice} - ${pricing.maxPrice} for requested agent")
//    }
    // TODO: Check that the paid session has funds equal to max cap of requested agents once coral-escrow has implemented

    logger.info { "Creating claim for paid session ${request.paidSessionId} and agent ${request.graphAgentRequest.id}" }

    return remoteSessionManager.createClaimNoPaymentCheck(
        agent = request.toGraphAgent(registry, true),
        paymentSessionId = request.paidSessionId,
        maxCost = pricing.maxPrice.toMicroCoral(jupiterService) // request.graphAgentReq uest matchingPaidAgentSessionEntry.cap
    )
}

