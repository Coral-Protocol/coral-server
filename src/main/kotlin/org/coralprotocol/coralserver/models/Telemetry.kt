@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject
import org.coralprotocol.coralserver.models.telemetry.generic.Message as GenericMessage
import org.coralprotocol.coralserver.models.telemetry.openai.Message as OpenAIMessage

@Serializable
@JsonClassDiscriminator("format")
sealed class TelemetryMessages() {
    @Suppress("unused")
    @Serializable
    @SerialName("OpenAI")
    data class OpenAI(val data: List<OpenAIMessage>) : TelemetryMessages()

    @Suppress("unused")
    @Serializable
    @SerialName("Generic")
    data class Generic(val data: List<GenericMessage>) : TelemetryMessages()
}

@Serializable
data class TelemetryTarget(
    val threadId: String,
    val messageId: String
)

@Serializable
@JsonIgnoreUnknownKeys
data class Document(
    val id: String,
    val text: String,

    // This can contain user-defined fields and values
)

@Serializable
data class Telemetry(
    val modelDescription: String,
    val preamble: String? = null,
    val resources: List<Document>,
    val tools: List<Document>,
    val temperature: Double? = null,
    val maxTokens: Long? = null,
    val additionalParams: JsonObject? = null,
    val messages: TelemetryMessages
)

@Serializable
data class TelemetryPost(
    val targets: List<TelemetryTarget>,
    val data: Telemetry
)