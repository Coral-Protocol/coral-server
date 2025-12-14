package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.AgentRemainingBudget
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.x402.X402PaymentRequired
import org.coralprotocol.coralserver.x402.X402ProxiedResponse
import org.coralprotocol.coralserver.x402.X402ProxyRequest
import org.coralprotocol.coralserver.x402.withinBudget
import org.coralprotocol.payment.blockchain.X402Service

@Resource("agent-rpc")
class Rpc {
    @Resource("rental-claim")
    class RentalClaim(val parent: Rpc = Rpc())

    @Resource("x402")
    class X402(val parent: Rpc = Rpc())
}

fun Route.agentRpcApi(
    localSessionManager: LocalSessionManager,
    x402Service: X402Service?
) {
    post<Rpc.RentalClaim>({
        summary = "Submit rental agent claim"
        description = "Requests a certain amount of money to be paid for a work done by a rental agent"
        operationId = "submitRentalClaim"
        securitySchemeNames("agentSecret")
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
    }) { claim ->
        val agent = call.principal<SessionAgent>()
            ?: throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

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

    post<Rpc.X402>({
        summary = "Request for x402 proxying"
        description = "Allows an agent to request that the server pays for an x402 service by proxy"
        operationId = "requestX402Proxy"
        securitySchemeNames("agentSecret")
        request {
            pathParameter<String>("agentSecret") {
                description = "The agent's unique secret"
            }
            body<X402ProxyRequest> {
                description = "The request to be proxied"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<X402ProxiedResponse> {
                    description = "Proxied response from the service"
                }
            }
        }
    }) {post ->
        if (x402Service == null)
            throw RouteException(HttpStatusCode.InternalServerError, "x402 proxying is not configured on this server")

        val request = call.receive<X402ProxyRequest>()
        val agent = call.principal<SessionAgent>()
            ?: throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }

        val response = client.post(request.endpoint) {
            contentType(ContentType.Application.Json)
            setBody(request.body.toString())
        }

        if (response.status == HttpStatusCode.PaymentRequired) {
            val response = apiJsonConfig.decodeFromString<X402PaymentRequired>(response.bodyAsText())
            val orderedBudgetResources = agent.x402BudgetedResources.sortedBy { it.priority }

            val (budgetedResource, paymentRequirement) = orderedBudgetResources.firstNotNullOfOrNull { budgetedResource ->
                val accepted = response.accepts.find { it.withinBudget(budgetedResource) }
                return@firstNotNullOfOrNull if (accepted == null) {
                    null
                } else Pair(budgetedResource, accepted)
            } ?: throw RouteException(HttpStatusCode.BadRequest, "This agent does not have funds budgeted for this request")

            // todo: unpack this function to not send the first request twice
            // todo: in the case of multiple valid budgets, use the prioritised budget from above (also requires unpacking)
            val result = x402Service.executeX402Payment(
                serviceUrl = request.endpoint,
                method = request.method,
                body = request.body.toString()
            ).getOrThrow() // todo: don't throw, the real result should be wrapped and sent back

            // todo: use actual consumed amount
            budgetedResource.remainingBudget -= paymentRequirement.maxAmountRequired.toULong()
            //logger.info { "agent ${agent.name} consumed ${paymentRequirement.maxAmountRequired.toULong()} from their x402 budgeted resource ${budgetedResource.resource}.  ${budgetedResource.remainingBudget} remains." }

            call.respondText(apiJsonConfig.encodeToString(X402ProxiedResponse(
                code = 200, // todo: use the service's actual response code
                body = result.responseBody
            )), ContentType.Application.Json)
        }
        else {
            call.respondText(apiJsonConfig.encodeToString(X402ProxiedResponse(
                code = response.status.value,
                body = response.body()
            )), ContentType.Application.Json)
        }
    }
}