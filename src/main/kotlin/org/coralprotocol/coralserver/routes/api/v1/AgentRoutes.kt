package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.agent.registry.AgentExport
import org.coralprotocol.coralserver.agent.registry.PublicRegistryAgent
import org.coralprotocol.coralserver.agent.registry.toPublic
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/agents")
class Agents

@Resource("/api/v1/agents/exported")
class ExportedAgents

fun Routing.agentApiRoutes(appConfig: ConfigCollection, sessionManager: SessionManager) {
    get<Agents>({
        summary = "Get available agents"
        description = "Fetches a list of all agents available to the Coral server"
        operationId = "getAvailableAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<PublicRegistryAgent>> {
                    description = "List of available agents"
                }
            }
        }
    }) {
        val agents = appConfig.registry.importedAgents.map { entry -> entry.value.toPublic(entry.key) }
        call.respond(HttpStatusCode.OK, agents)
    }

    get<ExportedAgents>({
        summary = "Gets exported agents"
        description = "Fetches agents the Coral server has exported to other servers"
        operationId = "getExportedAgents"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<AgentExport>> {
                    description = "List of exported agents"
                }
            }
        }
    }) {
        val agents = appConfig.registry.exportedAgents.map { entry -> entry.value.toPublic(entry.key) }
        call.respond(HttpStatusCode.OK, agents)
    }
}