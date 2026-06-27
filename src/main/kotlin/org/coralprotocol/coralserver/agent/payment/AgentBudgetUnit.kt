package org.coralprotocol.coralserver.agent.payment

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.*

const val MICRO_CENTS_TO_CENTS: ULong = 1_000_000U
const val MICRO_CENTS_TO_DOLLARS: ULong = 100_000_000U
const val AGENT_BUDGET_UNIT_FRACTION_DIGITS = 8

/**
 * Micro-cents (one-millionth of a cent) are used because of token cost.  LLM providers often choose to price their
 * models by the dollars per 1 million tokens.  This allows the smallest claim to be 1 token at a rate of
 * $0.01 per 1 million tokens.  This allows for a maximum budget of $92,233,720,368 (plenty enough!)
 */
@JvmInline
@Serializable
value class AgentBudgetUnit(val value: ULong = 0UL) : Comparable<AgentBudgetUnit> {
    operator fun plus(other: AgentBudgetUnit): AgentBudgetUnit =
        AgentBudgetUnit(value + other.value)

    operator fun minus(other: AgentBudgetUnit): AgentBudgetUnit =
        AgentBudgetUnit(value - other.value)

    operator fun times(multiplier: ULong): AgentBudgetUnit =
        AgentBudgetUnit(value * multiplier)

    operator fun times(multiplier: UInt): AgentBudgetUnit =
        AgentBudgetUnit(value * multiplier)

    operator fun div(divisor: ULong): AgentBudgetUnit =
        AgentBudgetUnit(value / divisor)

    operator fun div(divisor: UInt): AgentBudgetUnit =
        AgentBudgetUnit(value / divisor)

    override operator fun compareTo(other: AgentBudgetUnit): Int =
        value.compareTo(other.value)

    fun isZero() = value == 0UL
    fun isNotZero() = value != 0UL

    override fun toString(): String =
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            maximumFractionDigits = AGENT_BUDGET_UNIT_FRACTION_DIGITS
        }.format(value.toDouble() / MICRO_CENTS_TO_DOLLARS.toDouble())

    companion object {
        val ZERO: AgentBudgetUnit = AgentBudgetUnit(0UL)
    }

}