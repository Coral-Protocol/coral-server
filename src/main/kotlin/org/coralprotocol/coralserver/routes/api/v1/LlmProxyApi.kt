package org.coralprotocol.coralserver.routes.api.v1

import io.ktor.http.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject

fun Route.llmProxyRoutes() {
    val localSessionManager by inject<LocalSessionManager>()
    val llmProxyService by inject<LlmProxyService>()

    route("/llm-proxy/{agentSecret}/{proxyName}/{path...}") {
        handle {
            val agentSecret = call.parameters["agentSecret"]
                ?: throw RouteException(HttpStatusCode.BadRequest, "Missing agent secret")

            val proxyName = call.parameters["proxyName"]
                ?: throw RouteException(HttpStatusCode.BadRequest, "Missing proxy name")

            val agent = try {
                localSessionManager.locateAgent(agentSecret).agent
            } catch (_: SessionException.InvalidAgentSecret) {
                throw RouteException(HttpStatusCode.Unauthorized, "Invalid agent secret")
            }

            llmProxyService.proxyRequest(
                agent, agent.graphAgent.proxies[proxyName] ?: throw RouteException(
                    HttpStatusCode.BadRequest, "Unknown proxy name"
                ), call.parameters.getAll("path") ?: emptyList(), call
            )
        }
    }
}
