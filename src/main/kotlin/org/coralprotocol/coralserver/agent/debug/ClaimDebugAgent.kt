package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.payment.AgentClaimRequest
import org.coralprotocol.coralserver.agent.payment.AgentClaimResult
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.get
import org.coralprotocol.coralserver.dsl.registryAgent
import org.coralprotocol.coralserver.routes.api.v1.Rpc
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val CLAIM_AGENT_ID = "claim"
const val CLAIM_AGENT_VERSION = "1.0.1"
val CLAIM_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(CLAIM_AGENT_ID, CLAIM_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

const val DEBUG_CLAIM_NAME = "DEBUG_CLAIM"
const val DEBUG_CLAIM_DEPENDENCY = "DEBUG_DEPENDENCY"

val claimAgentModule = module {
    single(named(CLAIM_AGENT_ID)) {
        registryAgent(CLAIM_AGENT_ID) {
            val httpClient = get<HttpClient>()

            description = "Debug agent that tests the claiming system"
            summary = description

            readme =
                """
                    This agent defines one claim type called $DEBUG_CLAIM_NAME. The default value of this claim is 1 micro cent.
                    
                    This agent takes two options.  A list of quantities (CLAIM_QUANTITIES) and a list of descriptions (CLAIM_DESCRIPTIONS), the lengths of these 
                    lists must be equal.  The entries from each list make pairs.
                    
                    For every pair, a claim will be made with the specified quantity and description.  The delay between claims is controlled by CLAIM_DELAY.
                """.trimIndent()

            val claimDelay = unsignedIntOption("CLAIM_DELAY") {
                description = "Milliseconds of delay between each claim"
                default = 1000u
            }

            val claimQuantities = unsignedIntListOption("CLAIM_QUANTITIES") {
                description =
                    "The quantity of each claim made. If the claim value is left at it's default of 1 micro cent, this quantity effectively controls the claim amount."
                required = true
            }

            val claimDescriptions = stringListOption("CLAIM_DESCRIPTIONS") {
                description = "A description of each claim made"
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

            dependency(DEBUG_CLAIM_DEPENDENCY)
            claimType(DEBUG_CLAIM_NAME, "A debug claim", DEBUG_CLAIM_DEPENDENCY, AgentBudgetUnit(1u))

            debugRuntime(get()) { _, _, agent ->
                val claimDelay = claimDelay.get(agent).toInt().milliseconds
                val claimQuantities = claimQuantities.get(agent)
                val claimDescriptions = claimDescriptions.get(agent)
                val autoKill = autoKill.get(agent)
                val ignoreShouldExit = ignoreShouldExit.get(agent)
                val keepAlive = keepAlive.get(agent)

                if (claimQuantities.size != claimDescriptions.size)
                    agent.logger.error { "CLAIM_QUANTITIES and CLAIM_DESCRIPTIONS must be the same size" }

                if (claimQuantities.isEmpty())
                    agent.logger.error { "At least one claim must be specified" }

                for ((quantity, description) in claimQuantities.zip(claimDescriptions)) {
                    delay(claimDelay)

                    val result = httpClient.post(Rpc.Claim()) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(agent.secret)
                        setBody(
                            AgentClaimRequest(
                                quantity = quantity,
                                claimTypeName = DEBUG_CLAIM_NAME,
                                additionalDescription = description,
                                autoKill = autoKill,
                            )
                        )
                    }.body<AgentClaimResult>()

                    if (result.shouldExit && !ignoreShouldExit)
                        break
                }

                if (keepAlive)
                    delay(Duration.INFINITE)
            }
        }
    }
}