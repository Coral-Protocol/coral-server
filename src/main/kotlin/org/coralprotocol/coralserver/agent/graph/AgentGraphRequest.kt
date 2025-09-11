package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.session.CustomTool

@Serializable
class AgentGraphRequest(
    @Description("Every agent required for this agent graph")
    val agents: List<GraphAgentRequest>,

    @Description("A set, containing sets that define the agent groups by name")
    val groups: Set<Set<String>>,

    @Description("A map of custom tools to provide to the agents in this graph")
    val customTools: Map<String, CustomTool>,
) {
    /**
     * Converts this request into an [AgentGraph] using the provided [AgentRegistry].  Most of the work done by this
     * function is done by [AgentGraphRequest.toAgentGraph].
     *
     * @throws AgentRequestException if any of the [agents] cannot be converted into a [GraphAgent]
     * @throws AgentRequestException if any of the [agents] have duplicate names
     * @throws AgentRequestException if any of the [groups] contain references to agents not in [agents]
     * @throws AgentRequestException if any of the [agents] reference custom tools that don't exist inside of [customTools]
     */
    fun toAgentGraph(registry: AgentRegistry): AgentGraph {
        val duplicateAgentNames = agents.groupingBy { it.name }.eachCount().filter { it.value > 1 }
        if (duplicateAgentNames.isNotEmpty()) {
            throw AgentRequestException("Agent graph contains duplicate agent names: $duplicateAgentNames")
        }

        // Do not allow groups to reference agents that were not provided in this request
        val missingAgents = groups.flatten().filter {
            !agents.any { agent -> agent.name == it }
        }
        if (missingAgents.isNotEmpty()) {
            throw AgentRequestException("Agent graph groups contain missing agents: $missingAgents")
        }

        // Do not allow agents to reference custom tools that were not provided in this request
        for (agent in agents) {
            val missingTools = agent.customToolAccess.filter { !customTools.containsKey(it) }
            if (missingTools.isNotEmpty()) {
                throw AgentRequestException("Agent ${agent.name} contains custom tools that were not provided : $missingTools")
            }
        }

        return AgentGraph(
            agents = agents.associate {
                it.name to it.toGraphAgent(registry)
            },
            customTools = customTools,
            groups = groups
        )
    }
}
