@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface AgentEvent {
    @Serializable
    @SerialName("runtime_started")
    object RuntimeStarted : AgentEvent

    @Serializable
    @SerialName("runtime_stopped")
    object RuntimeStopped : AgentEvent
}