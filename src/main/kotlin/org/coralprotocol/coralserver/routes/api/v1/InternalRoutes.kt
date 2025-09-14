package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.AgentRemainingBudget
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager

@Resource("/api/v1/internal/claim/{remoteSessionId}")
class Claim(val remoteSessionId: String)

fun Route.internalRoutes(
    remoteSessionManager: RemoteSessionManager?,
    aggregatedPaymentClaimManager: AggregatedPaymentClaimManager?,
    jupiterService: JupiterService
) {
    post<Claim>({
        summary = "Claim payment"
        description = "API endpoint for agents to claim payment for their work.  This is used by exported agents"
        operationId = "claimPayment"
        request {
            pathParameter<String>("remoteSessionId") {
                description = "The remote session ID"
            }
            body<AgentPaymentClaimRequest> {
                description = "A description of the work done and the payment required"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<AgentRemainingBudget> {
                    description = "The remaining budget associated with the session"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Remote session not found"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "No payment associated with the session"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {claim ->
        if (remoteSessionManager == null || aggregatedPaymentClaimManager == null)
            throw RouteException(HttpStatusCode.InternalServerError, "Remote sessions are disabled")

        val request = call.receive<AgentPaymentClaimRequest>()
        val session = remoteSessionManager.findSession(claim.remoteSessionId)
            ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

        val remainingToClaim = try {
           aggregatedPaymentClaimManager.addClaim(request, session)
        }
        catch (e: IllegalArgumentException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message)
        }

        call.respond(AgentRemainingBudget(
            remainingBudget = remainingToClaim,
            coralUsdPrice = jupiterService.coralToUsd(1.0)
        ))
    }
}
