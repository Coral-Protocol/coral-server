package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue

@Serializable
@Description("A request for an agent")
data class GraphAgentRequest(
    @SerialName("agent_name")
    @Description("The name of the agent to run, this must match the name of the agent in the registry")
    val agentName: String,

    @Description("The arguments to pass to the agent")
    val options: Map<String, AgentOptionValue>,

    @Description("<todo description>")
    val blocking: Boolean?,

    @Description("<todo description>")
    val tools: Set<String>,

    @Description("The server that should provide this agent and the runtime to use")
    val provider: GraphAgentProvider
)
