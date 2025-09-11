package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/internal/claim/{remoteSessionId}")
class Claim(val remoteSessionId: String)

fun Route.claimRoutes(
    config: PaymentConfig,
    sessionManager: RemoteSessionManager,
    aggregatedPaymentClaimManager: AggregatedPaymentClaimManager
) {
    post<Claim>({
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
                body<Long> {
                    description = "Claim ID"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Remote session not found"
            }
        }
    }) {claim ->
        val request = call.receive<PaymentClaimRequest>()
        val session = sessionManager.findSession(claim.remoteSessionId)
            ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

        val remainingToClaim = aggregatedPaymentClaimManager.addClaim(request, session)
        logger.info {
            "Processing claim for session ${request.paymentSessionId}, agent ${request.agentId}, amount: ${request.amount}"
        }
        call.respond(remainingToClaim)
    }
}

@Serializable
data class PaymentClaimRequest(
    val paymentSessionId: Long,
    val agentId: String,
    val amount: Long
)
