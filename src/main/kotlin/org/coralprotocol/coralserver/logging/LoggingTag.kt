@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.logging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName

@Serializable
@JsonClassDiscriminator("type")
sealed interface LoggingTag {
    val mdcMap: Map<String, String>

    @Serializable
    @SerialName("namespace")
    data class Namespace(val namespace: String) : LoggingTag {
        override val mdcMap: Map<String, String> = mapOf("namespace" to namespace)
    }

    @Serializable
    @SerialName("session")
    data class Session(val sessionId: String) : LoggingTag {
        override val mdcMap: Map<String, String> = mapOf("sessionId" to sessionId)
    }

    @Serializable
    @SerialName("agent")
    data class Agent(val name: UniqueAgentName) : LoggingTag {
        override val mdcMap: Map<String, String> = mapOf("agentName" to name)
    }

    @Serializable
    @SerialName("sensitive")
    object Sensitive : LoggingTag {
        override val mdcMap: Map<String, String> = emptyMap()
    }
}

class LoggingTagFilter(
    namespaceFilter: String?,
    sessionFilter: String?,
    agentFilter: String?,
    val allowSensitive: Boolean,
) {
    val filters = buildList {
        namespaceFilter?.let { add(LoggingTag.Namespace(it)) }
        sessionFilter?.let { add(LoggingTag.Session(it)) }
        agentFilter?.let { add(LoggingTag.Agent(it)) }
    }

    fun filter(event: LoggingEvent): Boolean {
        if (event.tags.any { it is LoggingTag.Sensitive } && !allowSensitive)
            return false

        return filters.all { event.tags.contains(it) }
    }
}
