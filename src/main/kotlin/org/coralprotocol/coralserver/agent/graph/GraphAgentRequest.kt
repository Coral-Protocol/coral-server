package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier

@Serializable
@Description("A request for an agent.  GraphAgentRequest -> GraphAgent")
data class GraphAgentRequest(
    @Description("The ID of this agent in the registry")
    val id: AgentRegistryIdentifier,

    @Description("A given name for this agent in the session/group")
    val name: String,

    @Description("An optional override for the description of this agent")
    val description: String?,

    @Description("The arguments to pass to the agent")
    val options: Map<String, AgentOptionValue>,

    @Description("The system prompt/developer text/preamble passed to the agent")
    val systemPrompt: String?,

    @Description("All blocking agents in a group must be instantiated before the group can communicate.  Non-blocking agents' contributions to groups are optional")
    val blocking: Boolean?,

    @Description("A list of custom tools that this agent can access.  The custom tools must be defined in the parent AgentGraphRequest object")
    val customToolAccess: Set<String>,

    @Description("Optional Coral features that this agent should have access to")
    @SerialName("coralPlugins")
    val plugins: Set<GraphAgentPlugin>,

    @Description("The server that should provide this agent and the runtime to use")
    val provider: GraphAgentProvider
) {
    /**
     * Given a reference to the agent registry [AgentRegistry], this function will attempt to convert this request into
     * a [GraphAgent].  If [isRemote] is true, this function will ensure the [provider] is [GraphAgentProvider.Local]
     * and the [GraphAgentProvider.Local.runtime] is exported in the registry.
     *
     * @throws IllegalArgumentException if the agent registry cannot be resolved.
     */
    fun toGraphAgent(registry: AgentRegistry, isRemote: Boolean = false): GraphAgent {
        val registryAgent = registry.findAgent(id)
            ?: throw AgentRequestException("Agent $id not found in registry")

        // It is an error to specify unknown options
        val unknownOptions = options.filter { !registryAgent.options.containsKey(it.key) }
        if (unknownOptions.isNotEmpty()) {
            throw AgentRequestException("Agent $id contains unknown options: ${unknownOptions.keys.joinToString()}")
        }

        val allOptions = (options + registryAgent.defaultOptions).toMutableMap()

        // Options that are specified in the export settings take the highest priority, but they should only be
        // considered in a remote context
        allOptions += if (isRemote) {
            val runtime = when (provider) {
                is GraphAgentProvider.Local -> provider.runtime

                // Don't allow a remote request that requests another remote request
                is GraphAgentProvider.RemoteRequest, is GraphAgentProvider.Remote -> {
                    throw AgentRequestException("A request for a remote agent must also request a local provider")
                }
            }

            registryAgent.exportSettings[runtime]?.options
                ?: throw AgentRequestException("Runtime $runtime is not exported by agent $id")
        }
        else {
            mapOf()
        }

        val missingOptions = registryAgent.requiredOptions.filterKeys { !allOptions.containsKey(it) }
        if (missingOptions.isNotEmpty()) {
            throw AgentRequestException("Agent $id is missing required options: ${missingOptions.keys.joinToString()}")
        }

        return GraphAgent(
            registryAgent = registryAgent,
            name = name,
            description = description,
            options = allOptions,
            systemPrompt = systemPrompt,
            blocking = blocking,
            customToolAccess = customToolAccess,
            plugins = plugins,
            provider = provider,
        )
    }
}