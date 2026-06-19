package org.coralprotocol.coralserver.dsl

import org.coralprotocol.coralserver.agent.debug.CLAIM_AGENT_ID
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOptionValue
import kotlin.time.Duration

@CoralDsl
class ClaimAgentRequestBuilder(name: String) : GraphAgentRequestBuilder(
    RegistryAgentIdentifier(
        CLAIM_AGENT_ID, "1.0.0",
        AgentRegistrySourceIdentifier.Local
    ), name
) {
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
                "CLAIM_DELAY" to PolymorphicAgentOptionValue.UInt(claimDelay.inWholeMilliseconds.toUInt()),
                "CLAIM_QUANTITIES" to PolymorphicAgentOptionValue.ULongList(claims.map { it.first.value.toString() }),
                "CLAIM_DESCRIPTIONS" to PolymorphicAgentOptionValue.StringList(claims.map { it.second }),
                "AUTO_KILL" to PolymorphicAgentOptionValue.Boolean(autoKill),
                "IGNORE_SHOULD_EXIT" to PolymorphicAgentOptionValue.Boolean(ignoreShouldExit),
                "KEEP_ALIVE" to PolymorphicAgentOptionValue.Boolean(keepAlive),
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