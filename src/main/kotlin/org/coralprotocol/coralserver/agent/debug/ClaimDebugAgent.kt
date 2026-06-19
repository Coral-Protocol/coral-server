package org.coralprotocol.coralserver.agent.debug

import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_CENTS
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_DOLLARS
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val CLAIM_AGENT_ID = "claim"
const val CLAIM_AGENT_VERSION = "1.0.1"
val CLAIM_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(CLAIM_AGENT_ID, CLAIM_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

val claimAgentModule = module {
    single(named(CLAIM_AGENT_ID)) {
        registryAgent(CLAIM_AGENT_ID) {
            description = "Debug agent that tests the claiming system"
            summary = description

            readme =
                "Makes a number of claims described by CLAIM_AMOUNTS and CLAIM_DESCRIPTIONS.  After all claims have been made this agent will exit."

            val claimDelay = unsignedIntOption("CLAIM_DELAY") {
                description = "Milliseconds of delay between each claim"
                default = 1000u
            }

            val claimQuantities = unsignedLongListOption("CLAIM_QUANTITIES") {
                description =
                    "An amount for each claim.  The value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS."
                required = true
            }

            val claimDescriptions = stringListOption("CLAIM_DESCRIPTIONS") {
                description =
                    "A description for each claim.  If the length of this list is less than CLAIM_AMOUNTS, the last description will be used for the remaining claims."
                required = true
            }

            val autoKill = booleanOption("AUTO_KILL") {
                description = "Whether to request that this agent is automatically killed when posting a claim."
                default = false
            }

            val ignoreShouldExit = booleanOption("IGNORE_SHOULD_EXIT") {
                description = "Whether to ignore the shouldExit field in the response."
                default = false
            }

            val keepAlive = booleanOption("KEEP_ALIVE") {
                description = "If this is true, after all claims are made the agent will wait to be killed manually"
                default = false
            }

            debugRuntime(get()) { client, _, agent ->
                val claimDelayValue = claimDelay.get(agent).toInt().milliseconds
                val claimQuantitiesValue = claimQuantities.get(agent)
                val claimDescriptionsValue = claimDescriptions.get(agent)
                val autoKillValue = autoKill.get(agent)
                val ignoreShouldExitValue = ignoreShouldExit.get(agent)
                val keepAliveValue = keepAlive.get(agent)

                if (claimQuantitiesValue.size != claimDescriptionsValue.size)
                    agent.logger.error { "CLAIM_QUANTITIES and CLAIM_DESCRIPTIONS must be the same size" }

                if (claimQuantitiesValue.isEmpty())
                    agent.logger.error { "At least one claim must be specified" }

                for ((amount, description) in claimQuantitiesValue.zip(claimDescriptionsValue)) {
                    delay(claimDelayValue)

                    // todo: implementation of posting claim
                }

                if (keepAliveValue)
                    delay(Duration.INFINITE)
            }
        }
    }
}