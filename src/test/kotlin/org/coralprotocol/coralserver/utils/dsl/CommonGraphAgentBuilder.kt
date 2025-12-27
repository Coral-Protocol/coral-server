package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.registry.option.option
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.x402.X402BudgetedResource

@TestDsl
open class CommonGraphAgentBuilder(
    open var name: String,
) {
    var description: String? = null
    var systemPrompt: String? = null
    var blocking: Boolean = true
    var provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION)

    protected val plugins = mutableSetOf<GraphAgentPlugin>()
    protected val x402Budgets = mutableListOf<X402BudgetedResource>()

    fun plugin(plugin: GraphAgentPlugin) {
        plugins.add(plugin)
    }

    fun x402Budget(budget: X402BudgetedResource) {
        x402Budgets.add(budget)
    }
}

@TestDsl
class GraphAgentBuilder(name: String) : CommonGraphAgentBuilder(name) {
    private val registryAgentBuilder = RegistryAgentBuilder(name)
    private val options = mutableMapOf<String, AgentOptionWithValue>()
    private val customTools = mutableMapOf<String, GraphAgentTool>()

    fun option(key: String, value: AgentOptionWithValue) {
        options[key] = value
        registryAgentBuilder.option(key, value.option())
    }

    fun registryAgent(block: RegistryAgentBuilder.() -> Unit) {
        registryAgentBuilder.apply(block)
    }

    fun tool(key: String, tool: GraphAgentTool) {
        customTools[key] = tool
    }

    fun build(): GraphAgent {
        return GraphAgent(
            registryAgent = registryAgentBuilder.build(),
            name = name,
            description = description,
            options = options.toMap(),
            systemPrompt = systemPrompt,
            blocking = blocking,
            customTools = customTools.toMap(),
            plugins = plugins.toSet(),
            provider = provider,
            x402Budgets = x402Budgets.toList()
        )
    }
}

@TestDsl
class GraphAgentRequestBuilder(
    val identifier: RegistryAgentIdentifier,
    override var name: String = identifier.name
) : CommonGraphAgentBuilder(name) {
    private val options = mutableMapOf<String, AgentOptionValue>()
    private val customToolAccess = mutableSetOf<String>()

    fun option(key: String, value: AgentOptionValue) {
        options[key] = value
    }

    fun toolAccess(toolName: String) {
        customToolAccess.add(toolName)
    }

    fun buildRequest(): GraphAgentRequest {
        return GraphAgentRequest(
            id = identifier,
            name = name,
            description = description,
            options = options,
            systemPrompt = systemPrompt,
            blocking = blocking,
            customToolAccess = customToolAccess,
            plugins = plugins,
            provider = provider,
            x402Budgets = x402Budgets
        )
    }
}

fun graphAgent(name: String, block: GraphAgentBuilder.() -> Unit = {}): GraphAgent =
    GraphAgentBuilder(name).apply(block).build()

fun graphAgentRequest(
    identifier: RegistryAgentIdentifier,
    block: GraphAgentRequestBuilder.() -> Unit = {}
): GraphAgentRequest =
    GraphAgentRequestBuilder(identifier).apply(block).buildRequest()

fun graphAgentPair(name: String, block: GraphAgentBuilder.() -> Unit = {}): Pair<String, GraphAgent> =
    name to GraphAgentBuilder(name).apply(block).build()