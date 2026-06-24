package org.coralprotocol.coralserver.dsl

import org.coralprotocol.coralserver.agent.graph.*
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AnyAgentOptionWithValue
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.coralprotocol.coralserver.x402.X402BudgetedResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
    protected var budgetSettings: GraphAgentBudgetSettings = GraphAgentBudgetSettings()
    protected val x402Budgets = mutableListOf<X402BudgetedResource>()
    val proxies = mutableMapOf<String, LlmProxiedModel>()

    fun plugin(plugin: GraphAgentPlugin) {
        plugins.add(plugin)
    }

    fun annotation(name: String, value: String) {
        annotations[name] = value
    }

    fun budgetSettings(block: GraphAgentBudgetSettingsBuilder.() -> Unit) {
        budgetSettings = GraphAgentBudgetSettingsBuilder().apply(block).build()
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
    private val options = mutableMapOf<String, AnyAgentOptionWithValue>()
    private val customTools = mutableMapOf<String, GraphAgentTool>()

    fun option(key: String, value: AnyAgentOptionWithValue) {
        options[key] = value
        registryAgentBuilder.option(key, value.option)
    }

    fun stringOption(name: String, value: String, block: StringAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.stringOption(name, block).option.withValue(PolymorphicAgentOptionValue.String(value))
    }

    fun stringListOption(name: String, value: List<String>, block: StringListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.stringListOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.StringList(
                    value
                )
            )
    }

    fun blobOption(name: String, value: String, block: BlobAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.blobOption(name, block).option.withValue(PolymorphicAgentOptionValue.Blob(value))
    }

    fun blobListOption(name: String, value: List<String>, block: BlobListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.blobListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.BlobList(value))
    }

    fun booleanOption(name: String, value: Boolean, block: BooleanAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.booleanOption(name, block).option.withValue(PolymorphicAgentOptionValue.Boolean(value))
    }

    fun byteOption(name: String, value: Byte, block: ByteAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.byteOption(name, block).option.withValue(PolymorphicAgentOptionValue.Byte(value))
    }

    fun byteListOption(name: String, value: List<Byte>, block: ByteListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.byteListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.ByteList(value))
    }

    fun shortOption(name: String, value: Short, block: ShortAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.shortOption(name, block).option.withValue(PolymorphicAgentOptionValue.Short(value))
    }

    fun shortListOption(name: String, value: List<Short>, block: ShortListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.shortListOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.ShortList(
                    value
                )
            )
    }

    fun intOption(name: String, value: Int, block: IntAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.intOption(name, block).option.withValue(PolymorphicAgentOptionValue.Int(value))
    }

    fun intListOption(name: String, value: List<Int>, block: IntListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.intListOption(name, block).option.withValue(PolymorphicAgentOptionValue.IntList(value))
    }

    fun longOption(name: String, value: Long, block: LongAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.longOption(name, block).option.withValue(PolymorphicAgentOptionValue.Long(value))
    }

    fun longListOption(name: String, value: List<Long>, block: LongListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.longListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.LongList(value))
    }

    fun unsignedByteOption(name: String, value: UByte, block: UByteAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedByteOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.UByte(
                    value
                )
            )
    }

    fun unsignedByteListOption(name: String, value: List<UByte>, block: UByteListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedByteListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.UByteList(value))
    }

    fun unsignedShortOption(name: String, value: UShort, block: UShortAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedShortOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.UShort(
                    value
                )
            )
    }

    fun unsignedShortListOption(
        name: String,
        value: List<UShort>,
        block: UShortListAgentOptionBuilder.() -> Unit = {}
    ) {
        options[name] =
            registryAgentBuilder.unsignedShortListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.UShortList(value))
    }

    fun unsignedIntOption(name: String, value: UInt, block: UIntAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedIntOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.UInt(value))
    }

    fun unsignedIntListOption(name: String, value: List<UInt>, block: UIntListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedIntListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.UIntList(value))
    }

    fun unsignedLongOption(name: String, value: ULong, block: ULongAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedLongOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.ULong(
                    value.toString()
                )
            )
    }

    fun unsignedLongListOption(name: String, value: List<ULong>, block: ULongListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.unsignedLongListOption(
                name,
                block
            ).option.withValue(PolymorphicAgentOptionValue.ULongList(value.map { it.toString() }))
    }

    fun floatOption(name: String, value: Float, block: FloatAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.floatOption(name, block).option.withValue(PolymorphicAgentOptionValue.Float(value))
    }

    fun floatListOption(name: String, value: List<Float>, block: FloatListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.floatListOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.FloatList(
                    value
                )
            )
    }

    fun doubleOption(name: String, value: Double, block: DoubleAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.doubleOption(name, block).option.withValue(PolymorphicAgentOptionValue.Double(value))
    }

    fun doubleListOption(name: String, value: List<Double>, block: DoubleListAgentOptionBuilder.() -> Unit = {}) {
        options[name] =
            registryAgentBuilder.doubleListOption(name, block).option.withValue(
                PolymorphicAgentOptionValue.DoubleList(
                    value
                )
            )
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
            x402Budgets = x402Budgets.toList(),
            budgetSettings = budgetSettings,
            annotations = annotations.toMap(),
            proxies = proxies.toMap()
        )
    }
}

@CoralDsl
open class GraphAgentRequestBuilder(
    val identifier: RegistryAgentIdentifier,
    override var name: String = identifier.name
) : CommonGraphAgentBuilder(name) {
    protected val options = mutableMapOf<String, AgentOptionValue>()
    protected val customToolAccess = mutableSetOf<String>()
    protected val proxyOverrideMap = mutableMapOf<String, GraphAgentProxyRequest>()

    private fun option(name: String, value: AgentOptionValue) {
        options[name] = value
    }

    fun stringOption(name: String, value: String) {
        option(name, PolymorphicAgentOptionValue.String(value))
    }

    fun stringListOption(name: String, value: List<String>) {
        option(name, PolymorphicAgentOptionValue.StringList(value))
    }

    fun stringListOption(name: String, vararg values: String) {
        option(name, PolymorphicAgentOptionValue.StringList(values.toList()))
    }

    fun blobOption(name: String, value: String) {
        option(name, PolymorphicAgentOptionValue.Blob(value))
    }

    fun blobListOption(name: String, value: List<String>) {
        option(name, PolymorphicAgentOptionValue.BlobList(value))
    }

    fun booleanOption(name: String, value: Boolean) {
        option(name, PolymorphicAgentOptionValue.Boolean(value))
    }

    fun byteOption(name: String, value: Byte) {
        option(name, PolymorphicAgentOptionValue.Byte(value))
    }

    fun byteListOption(name: String, value: List<Byte>) {
        option(name, PolymorphicAgentOptionValue.ByteList(value))
    }

    fun shortOption(name: String, value: Short) {
        option(name, PolymorphicAgentOptionValue.Short(value))
    }

    fun shortListOption(name: String, value: List<Short>) {
        option(name, PolymorphicAgentOptionValue.ShortList(value))
    }

    fun intOption(name: String, value: Int) {
        option(name, PolymorphicAgentOptionValue.Int(value))
    }

    fun intListOption(name: String, value: List<Int>) {
        option(name, PolymorphicAgentOptionValue.IntList(value))
    }

    fun longOption(name: String, value: Long) {
        option(name, PolymorphicAgentOptionValue.Long(value))
    }

    fun longListOption(name: String, value: List<Long>) {
        option(name, PolymorphicAgentOptionValue.LongList(value))
    }

    fun unsignedByteOption(name: String, value: UByte) {
        option(name, PolymorphicAgentOptionValue.UByte(value))
    }

    fun unsignedByteListOption(name: String, value: List<UByte>) {
        option(name, PolymorphicAgentOptionValue.UByteList(value))
    }

    fun unsignedShortOption(name: String, value: UShort) {
        option(name, PolymorphicAgentOptionValue.UShort(value))
    }

    fun unsignedShortListOption(name: String, value: List<UShort>) {
        option(name, PolymorphicAgentOptionValue.UShortList(value))
    }

    fun unsignedIntOption(name: String, value: UInt) {
        option(name, PolymorphicAgentOptionValue.UInt(value))
    }

    fun unsignedIntListOption(name: String, value: List<UInt>) {
        option(name, PolymorphicAgentOptionValue.UIntList(value))
    }

    fun unsignedLongOption(name: String, value: ULong) {
        option(name, PolymorphicAgentOptionValue.ULong(value.toString()))
    }

    fun unsignedLongListOption(name: String, value: List<ULong>) {
        option(name, PolymorphicAgentOptionValue.ULongList(value.map { it.toString() }))
    }

    fun floatOption(name: String, value: Float) {
        option(name, PolymorphicAgentOptionValue.Float(value))
    }

    fun floatListOption(name: String, value: List<Float>) {
        option(name, PolymorphicAgentOptionValue.FloatList(value))
    }

    fun doubleOption(name: String, value: Double) {
        option(name, PolymorphicAgentOptionValue.Double(value))
    }

    fun doubleListOption(name: String, value: List<Double>) {
        option(name, PolymorphicAgentOptionValue.DoubleList(value))
    }

    fun toolAccess(toolName: String) {
        customToolAccess.add(toolName)
    }

    fun proxyOverride(requestName: String, override: GraphAgentProxyRequest) {
        proxyOverrideMap[requestName] = override
    }

    open fun buildRequest(): GraphAgentRequest {
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
            budgetSettings = budgetSettings,
            annotations = annotations.toMap(),
            proxies = proxyOverrideMap
        )
    }
}

@CoralDsl
class GraphAgentBudgetSettingsBuilder {
    var budget: AgentBudgetUnit = AgentBudgetUnit()
    var exhaustionBehavior: AgentBudgetExhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
    var claimTypeCosts: MutableMap<String, AgentBudgetUnit> = mutableMapOf()

    fun kill(block: GraphAgentBudgetKillBuilder.() -> Unit) {
        exhaustionBehavior = GraphAgentBudgetKillBuilder().apply(block).build()
    }

    fun consumeSession() {
        exhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
    }

    fun claimTypeCost(type: String, cost: AgentBudgetUnit) {
        claimTypeCosts[type] = cost
    }

    fun build(): GraphAgentBudgetSettings {
        return GraphAgentBudgetSettings(
            budget = budget,
            claimTypeCosts = claimTypeCosts,
            exhaustionBehavior = exhaustionBehavior
        )
    }
}

@CoralDsl
class GraphAgentBudgetKillBuilder {
    var minimum: AgentBudgetUnit = AgentBudgetUnit()
    var force: Boolean = false
    var delay: Duration = 100.milliseconds

    fun build(): AgentBudgetExhaustionBehavior.Kill {
        return AgentBudgetExhaustionBehavior.Kill(minimum, force, delay)
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