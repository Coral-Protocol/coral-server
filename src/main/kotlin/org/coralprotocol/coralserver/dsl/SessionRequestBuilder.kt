package org.coralprotocol.coralserver.dsl

import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_CENTS
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_DOLLARS
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.session.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@CoralDsl
class SessionRequestBuilder {
    private var agentGraphRequest: AgentGraphRequest = AgentGraphRequest(listOf())
    private var namespaceRequest: SessionNamespaceProvider = SessionNamespaceProvider.CreateIfNotExists(
        SessionNamespaceRequest("default")
    )
    private var executionSettings: SessionRequestExecution = SessionRequestExecution.Execute(SessionRuntimeSettings())
    private var budgetSettings: SessionBudgetSettings = SessionBudgetSettings()

    private val annotations: MutableMap<String, String> = mutableMapOf()

    fun agentGraphRequest(block: AgentGraphRequestBuilder.() -> Unit) {
        agentGraphRequest = AgentGraphRequestBuilder().apply(block).build()
    }

    fun useExistingNamespace(name: String) {
        namespaceRequest = SessionNamespaceProvider.UseExisting(name)
    }

    fun createNamespaceIfNotExists(block: SessionNamespaceRequestBuilder.() -> Unit) {
        namespaceRequest =
            SessionNamespaceProvider.CreateIfNotExists(namespaceRequest(block))
    }

    fun immediateExecution(block: SessionRuntimeSettingsBuilder.() -> Unit) {
        executionSettings = SessionRequestExecution.Execute(runtimeSettings(block))
    }

    fun deferExecution() {
        executionSettings = SessionRequestExecution.Defer
    }

    fun budgetSettings(block: SessionBudgetSettingsBuilder.() -> Unit) {
        budgetSettings = SessionBudgetSettingsBuilder().apply(block).build()
    }

    fun annotation(name: String, value: String) {
        annotations[name] = value
    }

    fun build(): SessionRequest {
        return SessionRequest(
            agentGraphRequest,
            namespaceRequest,
            executionSettings,
            budgetSettings,
            annotations
        )
    }
}

@CoralDsl
class AgentGraphRequestBuilder {
    private val agents = mutableListOf<GraphAgentRequest>()
    private val groups = mutableSetOf<Set<UniqueAgentName>>()
    private val tools = mutableMapOf<String, GraphAgentTool>()

    fun agent(identifier: RegistryAgentIdentifier, block: GraphAgentRequestBuilder.() -> Unit) {
        agents.add(graphAgentRequest(identifier, block))
    }

    fun claimAgent(name: String, block: ClaimAgentRequestBuilder.() -> Unit) {
        agents.add(ClaimAgentRequestBuilder(name).apply(block).buildRequest())
    }

    fun tool(name: String, tool: GraphAgentTool) {
        tools[name] = tool
    }

    fun groupAllAgents() {
        groups.clear()
        groups.add(agents.map { it.name }.toSet())
    }

    fun isolateAllAgents() {
        groups.clear()
        groups.addAll(agents.map { setOf(it.name) })
    }

    fun group(group: Set<UniqueAgentName>) {
        groups.add(group)
    }

    fun build(): AgentGraphRequest {
        return AgentGraphRequest(
            agents = agents,
            groups = groups,
            customTools = tools
        )
    }
}

@CoralDsl
class SessionRuntimeSettingsBuilder {
    var ttl: Duration? = null
    var persistenceMode: SessionPersistenceMode = SessionPersistenceMode.None
    var webhooks: SessionWebhooks = SessionWebhooks()
    var extendedEndReport = false

    fun webhooks(block: SessionWebhooksBuilder.() -> Unit) {
        webhooks = SessionWebhooksBuilder().apply(block).build()
    }

    fun build(): SessionRuntimeSettings {
        return SessionRuntimeSettings(ttl?.inWholeMilliseconds, extendedEndReport, persistenceMode, webhooks)
    }
}

@CoralDsl
class SessionWebhooksBuilder {
    private var sessionEnd: SessionEndWebhook? = null

    fun sessionEndUrl(url: String) {
        sessionEnd = SessionEndWebhook(url)
    }

    fun build(): SessionWebhooks {
        return SessionWebhooks(sessionEnd)
    }
}

@CoralDsl
@Suppress("unused")
class SessionBudgetSettingsBuilder {
    var budget: AgentBudgetUnit = AgentBudgetUnit()
    var exhaustionBehavior: SessionBudgetExhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent()

    fun killAgent(block: SessionBudgetKillAgentBuilder.() -> Unit) {
        exhaustionBehavior = SessionBudgetKillAgentBuilder().apply(block).build()
    }

    fun killSession(block: SessionBudgetKillSessionBuilder.() -> Unit) {
        exhaustionBehavior = SessionBudgetKillSessionBuilder().apply(block).build()
    }

    fun warn() {
        exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
    }

    fun build(): SessionBudgetSettings {
        return SessionBudgetSettings(budget, exhaustionBehavior)
    }
}

@CoralDsl
class SessionBudgetKillAgentBuilder {
    var minimum: AgentBudgetUnit = AgentBudgetUnit()
    var force: Boolean = false
    var delay: Duration = 100.milliseconds

    fun build(): SessionBudgetExhaustionBehavior.KillAgent {
        return SessionBudgetExhaustionBehavior.KillAgent(minimum, force, delay)
    }
}

@CoralDsl
class SessionBudgetKillSessionBuilder {
    var minimum: AgentBudgetUnit = AgentBudgetUnit()
    var delay: Duration = 200.milliseconds

    fun build(): SessionBudgetExhaustionBehavior.KillSession {
        return SessionBudgetExhaustionBehavior.KillSession(minimum, delay)
    }
}

@CoralDsl
class SessionNamespaceRequestBuilder {
    var name: String = "default"
    var deleteOnLastSessionExit = true
    private val annotations: MutableMap<String, String> = mutableMapOf()

    fun annotation(name: String, value: String) {
        annotations[name] = value
    }

    fun build(): SessionNamespaceRequest {
        return SessionNamespaceRequest(name, deleteOnLastSessionExit, annotations)
    }
}

fun namespaceRequest(block: SessionNamespaceRequestBuilder.() -> Unit) =
    SessionNamespaceRequestBuilder().apply(block).build()

fun runtimeSettings(block: SessionRuntimeSettingsBuilder.() -> Unit) =
    SessionRuntimeSettingsBuilder().apply(block).build()

fun sessionRequest(block: SessionRequestBuilder.() -> Unit): SessionRequest =
    SessionRequestBuilder().apply(block).build()

val Int.cents: AgentBudgetUnit
    get() = AgentBudgetUnit(this.toULong() * MICRO_CENTS_TO_CENTS)

@Suppress("unused")
val Double.cents: AgentBudgetUnit
    get() = AgentBudgetUnit((this * MICRO_CENTS_TO_CENTS.toDouble()).toULong())

// TODO: do something to avoid future accidents around negatives
val Int.dollars: AgentBudgetUnit
    get() = AgentBudgetUnit(this.toULong() * MICRO_CENTS_TO_DOLLARS)

@Suppress("unused")
val Double.dollars: AgentBudgetUnit
    get() = AgentBudgetUnit((this * MICRO_CENTS_TO_DOLLARS.toDouble()).toULong())
