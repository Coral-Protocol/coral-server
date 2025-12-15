package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier

@Serializable
@Description("The state of an agent running in a session")
data class SessionAgentState(
    @Description("The name given for this agent in the AgentGraph, this is unique in the graph")
    val name: UniqueAgentName,

    @Description("The identifier for this agent's registry entry.  See RegistryAgent for more information")
    val registryAgentIdentifier: RegistryAgentIdentifier,

    @Description("True when the agent is waiting for a message from another agent")
    val isWaiting: Boolean,

    @Description("True after agent process was launched and made a connection to the Coral MCP server.  The agent will not be responsive until connected")
    val isConnected: Boolean,

    @Description("The description of this agent, given to other agents in the graph")
    val description: String?,

    @Description("A list of agents that this agent is aware of, constructed from agent groups in the AgentGraph")
    val links: Set<UniqueAgentName>
)