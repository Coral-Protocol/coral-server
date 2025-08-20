@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class AgentOptionType {
    @SerialName("string")
    STRING,

    @SerialName("secret")
    SECRET,

    @SerialName("number")
    NUMBER,
}

@Serializable
@JsonClassDiscriminator("type")
sealed class AgentOption {
    abstract val description: kotlin.String?
    abstract val required: Boolean

    @Serializable
    @SerialName("string")
    data class String(
        override val description: kotlin.String? = null,
        val default: kotlin.String? = null
    ) : AgentOption() {
        override val required: Boolean = default == null
    }

    @Serializable
    @SerialName("number")
    data class Number(
        override val description: kotlin.String? = null,
        val default: Double? = null,
    ) : AgentOption() {
        override val required: Boolean = default == null
    }

    @Serializable
    @SerialName("secret")
    data class Secret(
        override val description: kotlin.String? = null,
    ) : AgentOption() {
        override val required: Boolean = true
    }
}

fun AgentOption.defaultAsValue(): AgentOptionValue? =
    when (this) {
        is AgentOption.String -> this.default?.let { AgentOptionValue.String(it) }
        is AgentOption.Number -> this.default?.let { AgentOptionValue.Number(it) }
        is AgentOption.Secret -> null
    }
