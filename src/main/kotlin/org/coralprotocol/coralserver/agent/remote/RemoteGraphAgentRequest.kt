package org.coralprotocol.coralserver.agent.remote

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

// todo: investigate whether this really needs to be a different type to GraphAgentRequest
@Serializable
@Description("The representation of an agent on the agent graph.  This refers to a registry agent by name")
data class RemoteGraphAgentRequest(
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