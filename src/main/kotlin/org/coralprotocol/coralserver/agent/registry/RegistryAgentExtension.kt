@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface RegistryAgentExtension {
    @Serializable
    @SerialName("marketplace")
    @JsonIgnoreUnknownKeys
    data class Marketplace(
        val iconUrl: String? = null,
        val developer: String? = null,

        @Serializable(with = InstantSerializer::class)
        val publishedAt: Instant
    )
}
