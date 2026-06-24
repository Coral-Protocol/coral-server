package org.coralprotocol.coralserver.dsl

import org.coralprotocol.coralserver.agent.debug.CLAIM_AGENT_ID
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
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

    val microCentClaims: MutableList<Pair<UInt, String>> = mutableListOf()

    /**
     * Note: this will only actually claim the specified amount if the claim DEBUG_CLAIM_MICRO_CENT was just changed
     * from its default value.
     */
    fun claimBudgetUnit(budgetUnit: AgentBudgetUnit, description: String) {
        microCentClaims.add(budgetUnit.value.toUInt() to description)
    }


    override fun buildRequest(): GraphAgentRequest {
        unsignedIntOption("CLAIM_DELAY", claimDelay.inWholeMilliseconds.toUInt())
        unsignedIntListOption("CLAIM_QUANTITIES", microCentClaims.map { it.first })
        stringListOption("CLAIM_DESCRIPTIONS", microCentClaims.map { it.second })
        booleanOption("AUTO_KILL", autoKill)
        booleanOption("IGNORE_SHOULD_EXIT", ignoreShouldExit)
        booleanOption("KEEP_ALIVE", keepAlive)

        return super.buildRequest()
    }
}