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
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.payment.AgentClaimRequest
import org.coralprotocol.coralserver.agent.payment.AgentClaimResponse
import org.coralprotocol.coralserver.payment.BlankX402Service
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.x402.X402PaymentRequired
import org.coralprotocol.coralserver.x402.X402ProxiedResponse
import org.coralprotocol.coralserver.x402.X402ProxyRequest
import org.coralprotocol.coralserver.x402.withinBudget
import org.coralprotocol.payment.blockchain.X402Service
import org.koin.ktor.ext.inject

@Resource("agent-rpc")
class Rpc(val parent: ApiV1 = ApiV1()) {
    @Resource("claim")
    class Claim(val parent: Rpc = Rpc())

    @Resource("x402")
    class X402(val parent: Rpc = Rpc())
}

fun Route.agentRpcApi() {
    val x402Service by inject<X402Service>()
    val json by inject<Json>()

    post<Rpc.Claim>({
        summary = "Submit an agent claim"
        description = "Submits a claim for work performed"
        operationId = "submitClaim"
        securitySchemeNames("agentSecret")
        request {
            body<AgentClaimRequest> {
                description = "A description of the work done and the payment required"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<AgentClaimResponse> {
                    description = "A response to the claim request"
                }
            }
            HttpStatusCode.Unauthorized to {
                description = "Bad agent secret provided"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { claim ->
        val agent = call.principal<SessionAgent>()
            ?: throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

        val claim = call.receive<AgentClaimRequest>()
        call.respond(agent.processClaim(claim.amount, claim.description, claim.autoKill))
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
    }) { post ->
        if (x402Service is BlankX402Service)
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
            val response = json.decodeFromString<X402PaymentRequired>(response.bodyAsText())
            val orderedBudgetResources = agent.x402BudgetedResources.sortedBy { it.priority }

            val (budgetedResource, paymentRequirement) = orderedBudgetResources.firstNotNullOfOrNull { budgetedResource ->
                val accepted = response.accepts.find { it.withinBudget(budgetedResource) }
                return@firstNotNullOfOrNull if (accepted == null) {
                    null
                } else Pair(budgetedResource, accepted)
            } ?: throw RouteException(
                HttpStatusCode.BadRequest,
                "This agent does not have funds budgeted for this request"
            )

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

            call.respondText(
                json.encodeToString(
                    X402ProxiedResponse(
                        code = 200, // todo: use the service's actual response code
                        body = result.responseBody
                    )
                ), ContentType.Application.Json
            )
        } else {
            call.respondText(
                json.encodeToString(
                    X402ProxiedResponse(
                        code = response.status.value,
                        body = response.body()
                    )
                ), ContentType.Application.Json
            )
        }
    }
}