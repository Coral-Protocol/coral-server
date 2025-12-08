package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.payment.blockchain.BlockchainService

@Resource("agent-rental")
class AgentRental {
    @Resource("reserve")
    class Reserve()

    @Resource("wallet")
    class Wallet()

    @Resource("catalog")
    class Catalog {
        @Resource("{name}/{version}")
        class Details(val name: String, val version: String) {
            @Transient
            val identifier = AgentRegistryIdentifier(name, version)
        }
    }
}

fun Route.agentRentalApi(
    wallet: Wallet?,
    registry: AgentRegistry,
    blockchainService: BlockchainService?,
    remoteSessionManager: RemoteSessionManager?,
) {
    post<AgentRental.Reserve>({
        summary = "Reserve a list of rental agents"
        description = "Reserves a list of rental agents"
        operationId = "reserveAgents"
        request {
            body<PaidGraphAgentRequest> {
                description = "A list of agents to claim"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<String> {
                    description = "Reservation ID"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "GraphAgentRequest is invalid in a remote context"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) {
        if (remoteSessionManager == null || blockchainService == null)
            throw RouteException(HttpStatusCode.InternalServerError, "Remote agents are disabled")

        val paidGraphAgentRequest = call.receive<PaidGraphAgentRequest>()

//        try {
//            val claimId =
//        var escrowSession: Session? = null
//        (0u..paymentConfig.sessionRetryCount).forEach { _ ->
//            val session = blockchainService.getEscrowSession(
//                sessionId = request.paidSessionId,
//                authorityPubkey = request.clientWalletAddress
//            )
//
//            escrowSession = session.getOrNull()
//            if (escrowSession != null)
//                return@forEach
//
//            delay(paymentConfig.sessionRetryDelay.toLong())
//        }
//
//        if (escrowSession == null)
//            throw AgentRequestException("The payment session ${request.paidSessionId} from ${request.clientWalletAddress} cannot be found on the blockchain")
//
//        val matchingPaidAgentSessionEntry = escrowSession.agents.find {
//            it.id == request.graphAgentRequest.name
//        } ?: throw AgentRequestException.SessionNotFundedException("No matching agent in paid session")
//
//        val provider = request.graphAgentRequest.provider as GraphAgentProvider.Local
//        val registryAgent = registry.findAgent(id = request.graphAgentRequest.id)
//            ?: throw AgentRequestException.SessionNotFundedException("No matching agent in registry")
//
//        val associatedExportSettings = registryAgent.exportSettings[provider.runtime]
//            ?: throw AgentRequestException.SessionNotFundedException("Requested runtime is not exported by agent")
//
//        val pricing = associatedExportSettings.pricing
//        if (!pricing.withinRange(AgentClaimAmount.MicroCoral(matchingPaidAgentSessionEntry.cap), jupiterService)) {
//            throw AgentRequestException.SessionNotFundedException("Paid session agent cap ${matchingPaidAgentSessionEntry.cap} is not within the pricing range ${pricing.minPrice} - ${pricing.maxPrice} for requested agent")
//        }
//        // TODO: Check that the paid session has funds equal to max cap of requested agents once coral-escrow has implemented
//
//        logger.info { "Creating claim for paid session ${request.paidSessionId} and agent ${request.graphAgentRequest.id}" }
//
//        return remoteSessionManager.createClaimNoPaymentCheck(
//            agent = request.toGraphAgent(registry, true),
//            paymentSessionId = request.paidSessionId,
//            maxCost = matchingPaidAgentSessionEntry.cap,
//            clientWalletAddress = request.clientWalletAddress,
//        )
//            call.respond(
//                HttpStatusCode.OK,
//                claimId
//            )
//        } catch (e: AgentRequestException) {
//            throw RouteException(HttpStatusCode.BadRequest, e)
//        }
    }

    get<AgentRental.Wallet>({
        summary = "Get wallet address for rental agents"
        description = "Returns the wallet address payments should be made to for renting agents from this server"
        operationId = "getPublicWallet"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<String> {
                    description = "The wallet address"
                }
            }
            HttpStatusCode.NotFound to {
                description = "No wallet configured on this server"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) {
        call.respond(HttpStatusCode.OK, wallet?.walletAddress ?: throw RouteException(
            HttpStatusCode.NotFound,
            "No wallet configured on this server"
        ))
    }

    get<AgentRental.Catalog>({
        summary = "Get available rental agents"
        description = "Returns a list of all agents available to rent from this server"
        operationId = "getRentalAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<PublicRegistryAgent>> {
                    description = "List of available agents"
                }
            }
        }
    }) {
        val agents = registry.listAgents().map { it.toPublic() }
        call.respond(HttpStatusCode.OK, agents)
    }

    get<AgentRental.Catalog.Details>({
        summary = "Get rental agent info"
        description = "Returns agent rental details for the specified agent"
        operationId = "getRentalAgentDetails"
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
                    description = "Error message"
                }
            }
        }
    }) {
        val agent = registry.findAgent(AgentRegistryIdentifier(it.name, it.version))
            ?: throw RouteException(HttpStatusCode.NotFound, "Agent with ${it.name}:${it.version} not found")

        call.respond(agent.exportSettings.toPublic())
    }
}