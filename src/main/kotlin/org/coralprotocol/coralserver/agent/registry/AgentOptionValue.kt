package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
sealed interface AgentOptionValue {
    val type: AgentOptionType
    fun tryFromJson(value: JsonPrimitive): AgentOptionValue?

    @Serializable
    data class String(val value: kotlin.String) : AgentOptionValue {
        override val type get() = AgentOptionType.STRING
        override fun toString(): kotlin.String {
            return value
        }
        override fun tryFromJson(value: JsonPrimitive): AgentOptionValue? {
            return if (value.isString) String(value.content) else null
        }
    }

    @Serializable
    data class Number(val value: Double) : AgentOptionValue {
        override val type get() = AgentOptionType.NUMBER
        override fun toString(): kotlin.String {
            return value.toString()
        }
        override fun tryFromJson(value: JsonPrimitive): AgentOptionValue? {
            return value.doubleOrNull?.let { Number(it) }
        }
    }
}