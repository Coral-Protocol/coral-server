package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.post
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.server.ExportManager
import org.coralprotocol.coralserver.session.CreateSessionRequest

private val logger = KotlinLogging.logger {}

@Serializable
@Description("The representation of an agent on the agent graph.  This refers to a registry agent by name")
data class ImportAgentRequest(
    @Description("The desired name of the agent")
    val name: String,

    @Description("The name of the agent in the exporting server's registry")
    val type: String,

    @Description("The options that are passed to the agent")
    val options: Map<String, AgentOptionValue>,

    @Description("The system prompt/developer text/preamble passed to the agent")
    val systemPrompt: String?,

    @Description("Additional MCP tools to be proxied to and handled by the importing server")
    val extraTools: Set<String>,

    @Description("The runtime to use from the exporting server")
    val runtime: RuntimeId
)

@Serializable
@Description("The import request body")
data class ImportRequest(
    val agents: List<ImportAgentRequest>,
)

@Resource("/api/v1/agents/import")
class ImportAgents()

fun Routing.importRoutes(manager: ExportManager, orchestrator: Orchestrator) {
    post<ImportAgents>({
        summary = "Import agents"
        description = "Requests agents for import"
        operationId = "importAgents"
        request {
            body<ImportRequest> {
                description = "Agent import request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
        }
    }) {
        val request = call.receive<ImportRequest>()
        for (agent in request.agents) {
            orchestrator.spawn(
                "N/A",
                GraphAgent(
                    name = agent.name,
                    options = agent.options,
                    systemPrompt = agent.systemPrompt,
                    extraTools = agent.extraTools,
                    blocking = false,
                    provider = GraphAgentProvider.Local(
                        runtime = agent.runtime
                    )
                ),
                agentName = agent.type,
                port = orchestrator.port,
                relativeMcpServerUri = ,
                sessionManager = TODO(),
            )
        }
        // spin up the agent
        // put it somewhere special so we know where to find it when /ws is hit
//        manager.addAgent()
        call.respond(HttpStatusCode.OK, "hi")
    }
}