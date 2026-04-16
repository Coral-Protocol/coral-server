package org.coralprotocol.coralserver.routes.api.v1

import io.ktor.http.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject

fun Route.llmProxyRoutes() {
    val localSessionManager by inject<LocalSessionManager>()
    val llmProxyService by inject<LlmProxyService>()
    val llmProxyConfig by inject<LlmProxyConfig>()

    route("/llm-proxy/{agentSecret}/{provider}/{path...}") {
        handle {
            if (!llmProxyConfig.enabled) {
                throw RouteException(HttpStatusCode.ServiceUnavailable, "LLM proxy is disabled")
            }

            val agentSecret = call.parameters["agentSecret"]
                ?: throw RouteException(HttpStatusCode.BadRequest, "Missing agent secret")
            val provider = call.parameters["provider"]
                ?: throw RouteException(HttpStatusCode.BadRequest, "Missing provider")
            
            val agent = try {
                localSessionManager.locateAgent(agentSecret).agent
            } catch (_: SessionException.InvalidAgentSecret) {
                throw RouteException(HttpStatusCode.Unauthorized, "Invalid agent secret")
            }

            llmProxyService.proxyRequest(agent, provider, call.parameters.getAll("path") ?: emptyList(), call)
        }
    }
}
