package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
sealed interface AgentOptionValue {
    val type: AgentOptionType

    @Serializable
    data class String(val value: kotlin.String) : AgentOptionValue {
        override val type get() = AgentOptionType.STRING
        override fun toString(): kotlin.String {
            return value
        }
    }

    @Serializable
    data class Number(val value: Double) : AgentOptionValue {
        override val type get() = AgentOptionType.NUMBER
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    companion object {
        fun tryFromJson(value: JsonPrimitive): AgentOptionValue? {
            if (value.isString) {
                return String(value.content)
            }
            return value.doubleOrNull?.let { Number(it) }
        }
    }
}