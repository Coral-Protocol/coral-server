package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.ktor.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.server.ExportManager

private val logger = KotlinLogging.logger {}

@Serializable
@Description("The representation of an agent on the agent graph.  This refers to a registry agent by name")
data class ImportAgentRequest(
    @Description("The name of the agent in the exporting server's registry")
    val name: String,

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
    val agents: Map<String, ImportAgentRequest>,
)

@Resource("/api/v1/import")
class ImportedAgents(
    val agents: HashMap<String, String>,
)

fun Routing.importRoutes(manager: ExportManager) {
    post<ImportedAgents>({
        summary = "Import agents"
        description = "Requests agents for import"
        operationId = "importAgents"
        request {
            body<List<ImportRequest>> {
                description = "Agent import request"
            }
        }
    }) {
        // spin up the agent
        // put it somewhere special so we know where to find it when /ws is hit
//        manager.addAgent()
    }
}