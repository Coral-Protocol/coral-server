package org.coralprotocol.coralserver.orchestrator

import com.sksamuel.hoplite.ConfigAlias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.coralprotocol.coralserver.models.AgentType
import org.coralprotocol.coralserver.models.AgentRuntime

@Serializable
data class AgentRegistry(
    val agents: Map<AgentType, AgentDefinition> = mapOf(),
) {
    fun get(agentType: AgentType): AgentDefinition {
        return agents[agentType] ?: throw IllegalArgumentException("AgentDefinition $agentType not found")
    }
}

@Serializable
data class AgentDefinition(
    val runtime: AgentRuntime,
    @SerialName("options")
    private val optionsList: List<AgentOption>
) {
    @Transient
    val options = optionsList.map { it.name to it }
        .toMapOnDuplicate { throw IllegalArgumentException("Duplicate options ${it.joinToString(",")}") }
}

fun <K, V> List<Pair<K, V>>.toMapOnDuplicate(onDuplicates: (duplicates: List<K>) -> Unit): Map<K, V> {
    val groups: Map<K, List<Pair<K, V>>> = groupBy { it.first }
    val duplicates = groups.filter { it.value.size > 1 }.map { it.key }
    if (duplicates.isNotEmpty()) {
        onDuplicates(duplicates)
    }
    return groups.mapValues { it.value.first().second }
}

@Serializable
sealed interface AgentOption {
    abstract val type: String
    val name: String
    val description: String?

    @Serializable
    @SerialName("string")
    data class Str(override val name: String, override val description: String? = null, val default: String? = null) :
        AgentOption {
        override val type get(): String = "string"
    }

    @Serializable
    @SerialName("number")
    data class Number(
        override val name: String,
        override val description: String? = null,
        val default: Double? = null
    ) : AgentOption {
        override val type get(): String = "number"
    }
}

sealed interface AgentOptionValue {
    abstract val type: String

    data class Str(val value: String) : AgentOptionValue {
        override val type get(): String = "string"
    }

    data class Num(val value: Double) : AgentOptionValue {
        override val type get(): String = "number"
    }

    companion object {
        fun tryFromJson(value: JsonPrimitive): AgentOptionValue? {
            if (value.isString) {
                return Str(value.content)
            }
            return value.doubleOrNull?.let { Num(it) }
        }
    }
}