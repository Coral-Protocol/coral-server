@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
sealed class GraphAgentServerScorerEffect {
    @Serializable
    @SerialName("flat")
    @Description("A flat negative or positive weight")
    data class Flat(val weight: Double) : GraphAgentServerScorerEffect()

    @Serializable
    @SerialName("multiplier")
    @Description("A multiplier weight, this effect will only multiply against attributes with a number type")
    data class Multiplier(val weight: Double) : GraphAgentServerScorerEffect()

    fun apply(value: Double = 0.0): Double {
        return when (this) {
            is Flat -> {
                weight
            }
            is Multiplier -> {
                value * weight
            }
        }
    }
}

@Serializable
@JsonClassDiscriminator("op")
sealed class GraphAgentServerCustomScorer {
    @Serializable
    @SerialName("is_true")
    @Description("The effect will be applied for every attribute of the specified type with a boolean true value")
    data class IsTrue(
        val type: GraphAgentServerAttributeType,
        val effect: GraphAgentServerScorerEffect.Flat
    ) : GraphAgentServerCustomScorer()

    @Serializable
    @SerialName("is_false")
    @Description("The effect will be applied for every attribute of the specified type with a boolean false value")
    data class IsFalse(
        val type: GraphAgentServerAttributeType,
        val effect: GraphAgentServerScorerEffect.Flat
    ) : GraphAgentServerCustomScorer()

    @Serializable
    @SerialName("is_present")
    @Description("The effect will be applied for every attribute of the specified type")
    data class IsPresent(
        val type: GraphAgentServerAttributeType,
        val effect: GraphAgentServerScorerEffect
    ) : GraphAgentServerCustomScorer()

    @Serializable
    @SerialName("is_not_present")
    @Description("The effect will be applied if the no attribute of the specified type is present")
    data class IsNotPresent(
        val type: GraphAgentServerAttributeType,
        val effect: GraphAgentServerScorerEffect.Flat
    ) : GraphAgentServerCustomScorer()

    @Serializable
    @SerialName("string_equal")
    @Description("The effect will be applied for every attribute of the specified type with a matching string value")
    data class StringEqual(
        val type: GraphAgentServerAttributeType,
        val string: String,
        val effect: GraphAgentServerScorerEffect.Flat
    ) : GraphAgentServerCustomScorer()

    @Serializable
    @SerialName("string_not_equal")
    @Description("The effect will be applied for every attribute of the specified type with a non-matching string value")
    data class StringNotEqual(
        val type: GraphAgentServerAttributeType,
        val string: String,
        val effect: GraphAgentServerScorerEffect.Flat
    ) : GraphAgentServerCustomScorer()

    fun getScore(server: GraphAgentServer): Double =
        when (this) {
            is IsTrue -> {
                server.attributes.filter {
                    it.type == type && it is GraphAgentServerAttribute.Boolean && it.value
                }.sumOf { effect.apply() }
            }
            is IsFalse -> {
                server.attributes.filter {
                    it.type == type && it is GraphAgentServerAttribute.Boolean && !it.value
                }.sumOf { effect.apply() }
            }
            is IsNotPresent -> {
                val condition =  server.attributes.firstOrNull {
                    it.type == type
                } == null

                if (condition) effect.apply() else 0.0
            }
            is IsPresent -> {
                server.attributes.filter {
                    it.type == type && it is GraphAgentServerAttribute.Boolean && !it.value
                }.sumOf {
                    when (it) {
                        is GraphAgentServerAttribute.Number -> effect.apply(it.value)
                        else -> effect.apply()
                    }
                }
            }
            is StringEqual -> {
                server.attributes.filter {
                    it.type == type && it is GraphAgentServerAttribute.String && it.value == string
                }.sumOf { effect.apply() }
            }
            is StringNotEqual -> {
                server.attributes.filter {
                    it.type == type && it is GraphAgentServerAttribute.String && it.value != string
                }.sumOf { effect.apply() }
            }
        }
}

@Serializable
@JsonClassDiscriminator("type")
sealed class GraphAgentServerScoring() {
    abstract fun getScore(server: GraphAgentServer): Double

    @Serializable
    @SerialName("custom")
    @Description("Custom server scoring.  Weights can be added on a flat or multiplier basis per attribute")
    data class Custom(
        val scorers: List<GraphAgentServerCustomScorer>
    ) : GraphAgentServerScoring() {
        override fun getScore(server: GraphAgentServer): Double {
            return scorers.sumOf { it.getScore(server) }
        }
    }

    @Serializable
    @SerialName("default")
    @Description("Default server scoring.  No weights assigned to any server attribute")
    class Default : GraphAgentServerScoring() {
        override fun getScore(server: GraphAgentServer): Double {
            return 1.0
        }
    }

    // todo: better defaults/presets
}