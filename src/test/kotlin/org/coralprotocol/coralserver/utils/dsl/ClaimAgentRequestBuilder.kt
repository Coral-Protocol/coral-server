package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.debug.ClaimDebugAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import kotlin.time.Duration

@TestDsl
class ClaimAgentRequestBuilder(name: String) : GraphAgentRequestBuilder(ClaimDebugAgent.identifier, name) {
    var claimDelay = Duration.ZERO
    var autoKill = false
    var ignoreShouldExit = false
    var keepAlive = false

    val claims = mutableListOf<Pair<AgentBudgetUnit, String>>()
    fun claim(amount: AgentBudgetUnit, description: String) = claims.add(amount to description)
    fun claim(claim: Pair<AgentBudgetUnit, String>) = claims.add(claim)

    override fun buildRequest(): GraphAgentRequest {
        return GraphAgentRequest(
            id = identifier,
            name = name,
            description = description,
            options = options + mapOf(
                "CLAIM_DELAY" to AgentOptionValue.UInt(claimDelay.inWholeMilliseconds.toUInt()),
                "CLAIM_AMOUNTS" to AgentOptionValue.ULongList(claims.map { it.first.value.toString() }),
                "CLAIM_DESCRIPTIONS" to AgentOptionValue.StringList(claims.map { it.second }),
                "AUTO_KILL" to AgentOptionValue.Boolean(autoKill),
                "IGNORE_SHOULD_EXIT" to AgentOptionValue.Boolean(ignoreShouldExit),
                "KEEP_ALIVE" to AgentOptionValue.Boolean(keepAlive),
            ),
            systemPrompt = systemPrompt,
            blocking = blocking,
            customToolAccess = customToolAccess,
            plugins = plugins,
            provider = provider,
            x402Budgets = x402Budgets,
            budgetSettings = budgetSettings,
            annotations = annotations.toMap(),
            proxies = proxyOverrideMap
        )
    }
}