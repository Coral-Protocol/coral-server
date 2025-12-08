package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.routing.Route
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.AgentRemainingBudget
import org.coralprotocol.coralserver.server.RouteException

@Resource("agent-rpc")
class Rpc {
    @Resource("rental-claim")
    class Claim()
}

fun Route.agentRpcApi() {
    post<Rpc.Claim>({
        summary = "Submit rental agent claim"
        description = "Requests a certain amount of money to be paid for a work done by a rental agent"
        operationId = "submitRentalClaim"
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
        TODO()
//        if (remoteSessionManager == null || aggregatedPaymentClaimManager == null)
//            throw RouteException(HttpStatusCode.InternalServerError, "Remote sessions are disabled")
//
//        val request = call.receive<AgentPaymentClaimRequest>()
//        val session = remoteSessionManager.findSession(claim.remoteSessionId)
//            ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")
//
//        val remainingToClaim = try {
//           aggregatedPaymentClaimManager.addClaim(request, session)
//        }
//        catch (e: IllegalArgumentException) {
//            throw RouteException(HttpStatusCode.BadRequest, e)
//        }
//
//        call.respond(AgentRemainingBudget(
//            remainingBudget = remainingToClaim,
//            coralUsdPrice = jupiterService.coralToUsd(1.0)
//        ))
    }
}