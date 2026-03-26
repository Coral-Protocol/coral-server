package org.coralprotocol.coralserver.routes.api.v1

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.llm.LlmProxyService
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject

fun Route.llmProxyRoutes() {
    val localSessionManager by inject<LocalSessionManager>()
    val llmProxyService by inject<LlmProxyService>()
    val llmProxyConfig by inject<LlmProxyConfig>()

    route("/llm-proxy/{agentSecret}/{provider}") {
        route("{path...}") {
            handle {
                if (!llmProxyConfig.enabled) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "LLM proxy is disabled")
                    return@handle
                }

                val agentSecret = call.parameters["agentSecret"]
                    ?: throw RouteException(HttpStatusCode.BadRequest, "Missing agent secret")
                val provider = call.parameters["provider"]
                    ?: throw RouteException(HttpStatusCode.BadRequest, "Missing provider")
                val path = call.parameters.getAll("path")?.joinToString("/") ?: ""

                val agent = try {
                    localSessionManager.locateAgent(agentSecret).agent
                } catch (_: SessionException.InvalidAgentSecret) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid agent secret")
                    return@handle
                }

                llmProxyService.proxyRequest(agent, provider, path, call)
            }
        }
    }
}
