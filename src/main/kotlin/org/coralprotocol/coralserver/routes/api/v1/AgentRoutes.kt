package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.orchestrator.PublicRegistryAgent
import org.coralprotocol.coralserver.orchestrator.toPublic
import org.coralprotocol.coralserver.session.SessionManager


private val logger = KotlinLogging.logger {}

@Resource("/api/v1/agents")
class Agents

fun Routing.agentApiRoutes(appConfig: ConfigCollection, sessionManager: SessionManager) {
    get<Agents>({
        summary = "Get agent registry"
        description = "Fetches a list of available agents"
        operationId = "getAgentRegistry"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<PublicRegistryAgent>> {
                    description = "List of available agents"
                }
            }
        }
    }) {
        val registry = appConfig.registry.map { entry -> entry.value.toPublic(entry.key.toString()) }
        call.respond(HttpStatusCode.OK, registry)
    }
}