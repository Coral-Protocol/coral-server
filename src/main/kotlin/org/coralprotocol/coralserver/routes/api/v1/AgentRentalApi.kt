package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.PublicRestrictedRegistryAgent
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.payment.blockchain.BlockchainService

@Resource("agent-rental")
class AgentRental {
    @Resource("reserve")
    class Reserve(val parent: AgentRental = AgentRental())

    @Resource("wallet")
    class Wallet(val parent: AgentRental = AgentRental())

    @Resource("catalog")
    class Catalog(val parent: AgentRental = AgentRental())
}

/**
 * WARNING!
 *
 * These routes are public.  Before extending these routes or modifying them, make sure that the modifications or new
 * routes do not users to gain any unnecessary access to system resources or information.
 */
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
            HttpStatusCode.Forbidden to {
                description = "This server is not configured to allow rental agents"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) {
        call.respond(
            HttpStatusCode.OK, wallet?.walletAddress
                ?: throw RouteException(HttpStatusCode.Forbidden)
        )
    }

    get<AgentRental.Catalog>({
        summary = "Get available rental agents"
        description = "Returns a list of all agents available to rent from this server"
        operationId = "getRentalAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<PublicRestrictedRegistryAgent>> {
                    description = "List of exported agents"
                }
            }
        }
    }) {
        call.respond(HttpStatusCode.OK, registry.getExportedAgents().map { it.toPublic() })
    }
}