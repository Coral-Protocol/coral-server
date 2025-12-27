package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.session.models.*
import kotlin.time.Duration

@TestDsl
class SessionRequestBuilder {
    private var agentGraphRequest: AgentGraphRequest = AgentGraphRequest(listOf())
    private var sessionRuntimeSettings: SessionRuntimeSettings = SessionRuntimeSettings()

    fun agentGraphRequest(block: AgentGraphRequestBuilder.() -> Unit) {
        agentGraphRequest = AgentGraphRequestBuilder().apply(block).build()
    }

    fun runtimeSettings(block: SessionRuntimeSettingsBuilder.() -> Unit) {
        sessionRuntimeSettings = SessionRuntimeSettingsBuilder().apply(block).build()
    }

    fun build(): SessionRequest {
        return SessionRequest(agentGraphRequest, sessionRuntimeSettings)
    }
}

@TestDsl
class AgentGraphRequestBuilder {
    private val agents = mutableListOf<GraphAgentRequest>()
    private val groups = mutableSetOf<Set<UniqueAgentName>>()
    private val tools = mutableMapOf<String, GraphAgentTool>()

    fun agent(identifier: RegistryAgentIdentifier, block: GraphAgentRequestBuilder.() -> Unit) {
        agents.add(graphAgentRequest(identifier, block))
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


@TestDsl
class SessionRuntimeSettingsBuilder {
    var ttl: Duration? = null
    var persistenceMode: SessionPersistenceMode = SessionPersistenceMode.None
    var webhooks: SessionWebhooks = SessionWebhooks()

    fun webhooks(block: SessionWebhooksBuilder.() -> Unit) {
        webhooks = SessionWebhooksBuilder().apply(block).build()
    }

    fun build(): SessionRuntimeSettings {
        return SessionRuntimeSettings(ttl?.inWholeMilliseconds, persistenceMode, webhooks)
    }
}

@TestDsl
class SessionWebhooksBuilder {
    private var sessionEnd: SessionEndWebhook? = null

    fun sessionEndUrl(url: String) {
        sessionEnd = SessionEndWebhook(url)
    }

    fun build(): SessionWebhooks {
        return SessionWebhooks(sessionEnd)
    }
}

fun sessionRequest(block: SessionRequestBuilder.() -> Unit): SessionRequest {
    return SessionRequestBuilder().apply(block).build()
}