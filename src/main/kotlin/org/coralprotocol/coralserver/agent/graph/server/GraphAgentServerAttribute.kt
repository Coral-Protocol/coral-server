package org.coralprotocol.coralserver.agent.graph.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class GraphAgentServerAttributeType {
    // possibly represented as a timezone
    @SerialName("geographic_location")
    GEOGRAPHIC_LOCATION,

    // wallet ID?
    @SerialName("attested_by")
    ATTESTED_BY,

    // todo: fill this out
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("format")
sealed class GraphAgentServerAttribute() {
    abstract val type: GraphAgentServerAttributeType

    @Serializable
    @SerialName("string")
    data class String(
        override val type: GraphAgentServerAttributeType,
        val value: kotlin.String
    ) : GraphAgentServerAttribute()

    @Serializable
    @SerialName("number")
    data class Number(
        override val type: GraphAgentServerAttributeType,
        val value: Double
    ) : GraphAgentServerAttribute()

    @Serializable
    @SerialName("boolean")
    data class Boolean(
        override val type: GraphAgentServerAttributeType,
        val value: kotlin.Boolean
    ) : GraphAgentServerAttribute()
}