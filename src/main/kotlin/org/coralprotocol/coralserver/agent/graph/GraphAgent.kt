package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.session.CustomTool

@Serializable
@Description("The representation of an agent on the agent graph.  This refers to a registry agent by name")
data class GraphAgent(
    @Description("The name of the agent in the registry")
    val name: String,

    @Description("The options that are passed to the agent")
    val options: Map<String, AgentOptionValue>,

    @SerialName("system_prompt")
    @Description("The system prompt/developer text/preamble passed to the agent")
    val systemPrompt: String?,

    @SerialName("extra_tools")
    @Description("<todo description>")
    val extraTools: Set<String>,

    @Description("<todo description>")
    val blocking: Boolean
)

@Serializable
@Description("A graph of agents, tools and links between them.  The agent links define agent groups")
data class AgentGraph(
    @Description("A map of agent names to graph agents")
    val agents: Map<String, GraphAgent>,

    @Description("<todo description>")
    val tools: Map<String, CustomTool>,

    @Description("<todo description>")
    val links: Set<Set<String>>,
)

