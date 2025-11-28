package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.x402.X402PaymentRequired
import org.coralprotocol.coralserver.x402.X402ProxiedResponse
import org.coralprotocol.coralserver.x402.X402ProxyRequest
import org.coralprotocol.coralserver.x402.withinBudget
import org.coralprotocol.payment.blockchain.X402Service

private val logger = KotlinLogging.logger {}

@Resource("x402/{agentSecret}")
class X402Proxy(val agentSecret: String)

fun Route.x402Routes(localSessionManager: LocalSessionManager, x402Service: X402Service?) {
    post<X402Proxy>({
        summary = ""
        description = ""
        operationId = ""
        request {
            pathParameter<String>("agentSecret") {
                description = "The agent's unique secret"
            }
            body<X402ProxyRequest> {
                description = ""
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
        }
    }) {post ->
        if (x402Service == null)
            throw RouteException(HttpStatusCode.InternalServerError, "x402 proxying is not configured on this server")

        val request = call.receive<X402ProxyRequest>()
        val agent = localSessionManager.locateAgent(post.agentSecret).agent

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
            logger.info { "agent ${agent.name} consumed ${paymentRequirement.maxAmountRequired.toULong()} from their x402 budgeted resource ${budgetedResource.resource}.  ${budgetedResource.remainingBudget} remains." }

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