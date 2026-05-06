package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.compareTypeWithValue
import org.coralprotocol.coralserver.agent.registry.option.requireValue
import org.coralprotocol.coralserver.agent.registry.option.withValue
import org.coralprotocol.coralserver.llmproxy.LlmProxyException
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.session.SessionResource
import org.coralprotocol.coralserver.x402.X402BudgetedResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
@Description("A request for an agent.  GraphAgentRequest -> GraphAgent")
data class GraphAgentRequest(
    @Description("The ID of this agent in the registry")
    val id: RegistryAgentIdentifier,

    @Description("A given name for this agent in the session/group")
    val name: String,

    @Description("An optional override for the description of this agent")
    val description: String? = null,

    @Description("The arguments to pass to the agent")
    @Optional
    val options: Map<String, AgentOptionValue> = emptyMap(),

    @Description("The system prompt/developer text/preamble passed to the agent")
    val systemPrompt: String? = null,

    @Description("All blocking agents in a group must be instantiated before the group can communicate.  Non-blocking agents' contributions to groups are optional")
    val blocking: Boolean? = null,

    @Description("A list of custom tools that this agent can access.  The custom tools must be defined in the parent AgentGraphRequest object")
    @Optional
    val customToolAccess: Set<String> = emptySet(),

    @Description("Plugins that should be installed on this agent.  See GraphAgentPlugin for more information")
    @Optional
    val plugins: Set<GraphAgentPlugin> = emptySet(),

    @Description("The server that should provide this agent and the runtime to use")
    val provider: GraphAgentProvider,

    @Description("An optional list of resources and an accompanied budget that this agent may spend on services that accept x402 payments")
    @Optional
    val x402Budgets: List<X402BudgetedResource> = emptyList(),

    @Description("A map where the key is the name of the proxy request and the value is the configuration and model that should be selected.")
    val proxies: Map<String, GraphAgentProxyRequest> = emptyMap(),

    @Optional
    override val annotations: Map<String, String> = emptyMap(),
) : SessionResource, KoinComponent {
    val agentRegistry by inject<AgentRegistry>()
    val llmProxyService by inject<LlmProxyService>()

    /**
     * Given a reference to the agent registry [AgentRegistry], this function will attempt to convert this request into
     * a [GraphAgent].  If [isRemote] is true, this function will ensure the [provider] is [GraphAgentProvider.Local]
     * and the [GraphAgentProvider.Local.runtime] is exported in the registry.
     *
     * @throws IllegalArgumentException if the agent registry cannot be resolved.
     */
    suspend fun toGraphAgent(
        customTools: Map<String, GraphAgentTool> = mapOf(),
        isRemote: Boolean = false
    ): GraphAgent {
        val restrictedRegistryAgent = agentRegistry.resolveAgent(id)
        restrictedRegistryAgent.restrictions.forEach { it.requireNotRestricted(this) }

        val registryAgent = restrictedRegistryAgent.registryAgent

        // It is an error to specify unknown options
        val unknownOptions = options.filter { !registryAgent.options.containsKey(it.key) }
        if (unknownOptions.isNotEmpty()) {
            throw AgentRequestException("Agent $id contains unknown options: ${unknownOptions.keys.joinToString()}")
        }

        val wrongTypes = options.filter { !registryAgent.options[it.key]!!.compareTypeWithValue(it.value) }
        if (wrongTypes.isNotEmpty()) {
            throw AgentRequestException("Agent $id contains wrong types for options: ${wrongTypes.keys.joinToString()}")
        }

        val allOptions = (registryAgent.defaultOptions + options)
            .mapValues { registryAgent.options[it.key]!!.withValue(it.value) }
            .toMutableMap()

        allOptions.forEach { (optionName, optionValue) ->
            try {
                optionValue.requireValue()
            } catch (e: AgentOptionValidationException) {
                throw AgentRequestException("Value given for option \"$optionName\" is invalid: ${e.message}")
            }
        }

        // Options that are specified in the export settings take the highest priority, but they should only be
        // considered in a remote context
        allOptions += if (isRemote) {
            val runtime = when (provider) {
                is GraphAgentProvider.Local -> provider.runtime
                is GraphAgentProvider.Linked -> provider.runtime

                // Don't allow a remote request that requests another remote request
                is GraphAgentProvider.RemoteRequest, is GraphAgentProvider.Remote -> {
                    throw AgentRequestException("A request for a remote agent must also request a local provider")
                }
            }

            // Export settings are validated (option name, value type, value validation) so it is safe to simply copy
            // export settings in here
            registryAgent.exportSettings[runtime]?.options
                ?.mapValues {
                    registryAgent.options[it.key]!!.withValue(it.value)
                }
                ?: throw AgentRequestException("Runtime $runtime is not exported by agent $id")
        } else {
            mapOf()
        }

        val missingOptions = registryAgent.requiredOptions.filterKeys { !allOptions.containsKey(it) }
        if (missingOptions.isNotEmpty()) {
            throw AgentRequestException("Agent $id is missing required options: ${missingOptions.keys.joinToString()}")
        }

        val resolvedProxies = registryAgent.llmProxies.associate { request ->
            when (val override = proxies[request.name]) {
                null -> try {
                    request.name to llmProxyService.resolveAgentProxyRequest(request)
                } catch (e: LlmProxyException) {
                    throw AgentRequestException("Could not resolve proxy request for agent $id: ${e.message}")
                }

                else -> {
                    val llmProxiedModel = llmProxyService.resolveAgentProxyRequest(override)
                    if (llmProxiedModel.providerConfig.format != request.format)
                        throw AgentRequestException("Requested configuration \"${override.configurationName}\" has format type ${llmProxiedModel.providerConfig.format} but agent $id requires format type ${request.format} for proxy request ${request.name}")

                    if (!llmProxiedModel.providerConfig.allowAnyModel
                        && !llmProxiedModel.providerConfig.models.contains(override.modelName)
                    ) {
                        throw AgentRequestException("Requested model \"${override.modelName}\" is not supported by configuration \"${override.configurationName}\" for proxy request ${request.name}")
                    }

                    request.name to llmProxiedModel
                }
            }
        }

        return GraphAgent(
            registryAgent = registryAgent,
            name = name,
            description = description,
            options = allOptions,
            systemPrompt = systemPrompt,
            blocking = blocking,
            customTools = customTools.filterKeys { customToolAccess.contains(it) },
            plugins = plugins,
            provider = provider,
            x402Budgets = x402Budgets,
            annotations = annotations,
            proxies = resolvedProxies
        )
    }
}
