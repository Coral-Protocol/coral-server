package org.coralprotocol.coralserver.session

import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.coralprotocol.coralserver.agent.registry.LocalAgentRegistry
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.api.v1.sessionApi
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

open class SessionBuildingE2E(val agents: List<RegistryAgent>) {
    val config = Config()
    val applicationRuntimeContext = ApplicationRuntimeContext(config)
    val jupiterService = JupiterService()
    val mcpToolManager = McpToolManager()
    val sessionManager = LocalSessionManager(null, applicationRuntimeContext, jupiterService, mcpToolManager)
    val registry = LocalAgentRegistry(agents)

    protected fun env(body: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                install(io.ktor.server.resources.Resources)
                routing {
                    mcpRoutes(sessionManager)
                    sessionApi(registry, sessionManager)
                }
                install(ServerContentNegotiation) {
                    json(apiJsonConfig, contentType = ContentType.Application.Json)
                }
                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        var wrapped = cause
                        if (cause !is RouteException) {
                            wrapped = RouteException(HttpStatusCode.InternalServerError, cause)
                        }

                        call.respondText(text = apiJsonConfig.encodeToString(wrapped), status = wrapped.status)
                    }
                }
            }

            client = createClient {
                install(Resources)
                install(SSE)
                install(ClientContentNegotiation) {
                    json(apiJsonConfig, contentType = ContentType.Application.Json)
                }
            }

            body()
        }
    }
}