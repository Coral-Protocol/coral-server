package org.coralprotocol.coralserver.dsl

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import org.coralprotocol.coralserver.agent.graph.*
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.registry.option.option
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.x402.X402BudgetedResource

@CoralDsl
open class CommonGraphAgentBuilder(
    open var name: String,
) {
    var description: String? = null
    var systemPrompt: String? = null
    var blocking: Boolean = true
    var provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION)

    protected val annotations: MutableMap<String, String> = mutableMapOf()
    protected val plugins = mutableSetOf<GraphAgentPlugin>()
    protected val x402Budgets = mutableListOf<X402BudgetedResource>()
    protected val proxies = mutableMapOf<String, LlmProxiedModel>()

    fun plugin(plugin: GraphAgentPlugin) {
        plugins.add(plugin)
    }

    fun annotation(name: String, value: String) {
        annotations[name] = value
    }

    fun x402Budget(budget: X402BudgetedResource) {
        x402Budgets.add(budget)
    }

    fun proxy(name: String, model: LlmProxiedModel) {
        proxies[name] = model
    }
}

@CoralDsl
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

    fun localTool(
        name: String,
        description: String,
        inputSchema: ToolSchema = ToolSchema(),
        outputSchema: ToolSchema = ToolSchema(),
        title: String? = null,
        annotations: ToolAnnotations? = null,
        function: suspend (agent: SessionAgent, arguments: JsonObject?) -> CallToolResult
    ) {
        customTools[name] = GraphAgentTool(
            description = description,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            title = title,
            annotations = annotations,
            transport = GraphAgentToolTransport.Local(function)
        )
    }

    fun prototypeRuntime(
        proxy: String,
        block: PrototypeRuntimeBuilder.() -> Unit = {}
    ) {
        provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
        registryAgent {
            runtime(PrototypeRuntimeBuilder(proxy).apply(block).build())
        }
    }

    fun functionRuntime(
        volatile: Boolean = false,
        block: suspend (executionContext: SessionAgentExecutionContext, applicationRuntimeContext: ApplicationRuntimeContext) -> Unit
    ) {
        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
        registryAgent {
            runtime(FunctionRuntime(volatile = volatile, function = block))
        }
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
            x402Budgets = x402Budgets.toList(),
            annotations = annotations.toMap(),
            proxies = proxies.toMap()
        )
    }
}

@CoralDsl
class GraphAgentRequestBuilder(
    val identifier: RegistryAgentIdentifier,
    override var name: String = identifier.name
) : CommonGraphAgentBuilder(name) {
    private val options = mutableMapOf<String, AgentOptionValue>()
    private val customToolAccess = mutableSetOf<String>()
    private val proxyOverrideMap = mutableMapOf<String, GraphAgentProxyRequest>()

    fun option(key: String, value: AgentOptionValue) {
        options[key] = value
    }

    fun toolAccess(toolName: String) {
        customToolAccess.add(toolName)
    }

    fun proxyOverride(requestName: String, override: GraphAgentProxyRequest) {
        proxyOverrideMap[requestName] = override
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
            x402Budgets = x402Budgets,
            annotations = annotations.toMap(),
            proxies = proxyOverrideMap
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

@CoralDsl
class AgentGraphBuilder {
    private val agents = mutableMapOf<String, GraphAgent>()
    private val groups = mutableSetOf<Set<UniqueAgentName>>()
    private val customTools = mutableMapOf<String, GraphAgentTool>()

    fun agent(name: String, block: GraphAgentBuilder.() -> Unit = {}) {
        agents[name] = GraphAgentBuilder(name).apply(block).build()
    }

    fun group(vararg names: String) {
        groups.add(names.toSet())
    }

    fun tool(name: String, tool: GraphAgentTool) {
        customTools[name] = tool
    }

    fun build(): AgentGraph = AgentGraph(
        agents = agents.toMap(),
        groups = groups.toSet(),
        customTools = customTools.toMap()
    )
}

fun agentGraph(block: AgentGraphBuilder.() -> Unit): AgentGraph = AgentGraphBuilder().apply(block).build()

fun org.coralprotocol.coralserver.session.LocalSession.addAgent(
    name: String,
    block: GraphAgentBuilder.() -> Unit = {}
): org.coralprotocol.coralserver.session.SessionAgent {
    val graphAgent = GraphAgentBuilder(name).apply(block).build()
    return addAgent(graphAgent)
}

fun org.coralprotocol.coralserver.session.SessionAgentExecutionContext.addAgent(
    name: String,
    block: GraphAgentBuilder.() -> Unit = {}
): org.coralprotocol.coralserver.session.SessionAgent {
    val newAgent = agent.session.addAgent(name, block)
    agent.session.launchAgents()
    return newAgent
}
