package org.coralprotocol.coralserver.agent.payment

import kotlinx.serialization.Serializable

const val MICRO_CENTS_TO_CENTS = 1_000_000
const val MICRO_CENTS_TO_DOLLARS = 100_000_000

/**
 * Micro-cents (one-millionth of a cent) are used because of token cost.  LLM providers often choose to price their
 * models by the dollars per 1 million tokens.  This allows the smallest claim to be 1 token at a rate of
 * $0.01 per 1 million tokens.  This allows for a maximum budget of $92,233,720,368 (plenty enough!)
 */
@JvmInline
@Serializable
value class AgentBudgetUnit(val value: Long)