@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionAgentStatus {
    @Serializable
    @SerialName("running")
    @Description("The agent is running and potentially has a connection with the agent's MCP server")
    data class Running(
        val connectionStatus: SessionAgentConnectionStatus,
    ) : SessionAgentStatus

    @Serializable
    @SerialName("waiting")
    @Description("The agent is waiting to be launched")
    object Waiting : SessionAgentStatus

    @Serializable
    @SerialName("stopped")
    @Description("The agent's runtime started and then subsequently stopped")
    object Stopped : SessionAgentStatus
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionAgentConnectionStatus {
    @Serializable
    @SerialName("connected")
    @Description("The agent has an active MCP connection with the agent's MCP server")
    data class Connected(
        val communicationStatus: SessionAgentCommunicationStatus,
    ) : SessionAgentConnectionStatus

    @Serializable
    @SerialName("not_connected")
    @Description("The agent is not connected to the agent's MCP server, it may be trying to connect actively or it may have encountered an error")
    object NotConnected : SessionAgentConnectionStatus
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionAgentCommunicationStatus {
    @Serializable
    @SerialName("thinking")
    @Description("The agent is not waiting for messages or sleeping and is assumed to be thinking")
    object Thinking : SessionAgentCommunicationStatus {
        override fun toString(): String {
            return "thinking"
        }
    }

    @Serializable
    @SerialName("waiting_message")
    @Description("The agent is waiting for a message")
    object WaitingMessage : SessionAgentCommunicationStatus {
        override fun toString(): String {
            return "waiting"
        }
    }

    @Serializable
    @SerialName("sleeping")
    @Description("The agent is sleeping")
    object Sleeping : SessionAgentCommunicationStatus {
        override fun toString(): String {
            return "sleeping"
        }
    }
}