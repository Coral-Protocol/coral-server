@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface RegistryAgentLicense {
    @Serializable
    @SerialName("sdpx")
    data class Spdx(val expression: String) : RegistryAgentLicense

    @Serializable
    @SerialName("text")
    data class Text(
        @Serializable(with = RegistryAgentStringSerializer::class)
        val text: String
    ) : RegistryAgentLicense
}