package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentBudgetExhaustionBehavior
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.session.reporting.SessionEndReport
import org.coralprotocol.coralserver.session.state.SessionState
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.coralprotocol.coralserver.utils.dsl.SessionRequestBuilder
import org.coralprotocol.coralserver.utils.dsl.cents
import org.coralprotocol.coralserver.utils.dsl.dollars
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.core.component.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TestAgentBudgets : CoralTest({
    suspend fun sessionEndReport(block: SessionRequestBuilder.() -> Unit): SessionEndReport {
        val client by inject<HttpClient>()
        val json by inject<Json>()
        val application by inject<Application>()
        val config by inject<NetworkConfig>()
        val localSessionManager by inject<LocalSessionManager>()

        val webhookPath = "webhook"
        val sessionEndReportDeferred = CompletableDeferred<SessionEndReport>()

        application.routing {
            post(webhookPath) {
                try {
                    sessionEndReportDeferred.complete(signatureVerifiedBody(json, config.webhookSecret))
                } catch (e: Exception) {
                    sessionEndReportDeferred.completeExceptionally(e)
                }
            }
        }

        client.authenticatedPost(LocalSessions.Session()) {
            setBody(
                sessionRequest {
                    block()
                    immediateExecution {
                        extendedEndReport = true
                        webhooks {
                            sessionEndUrl(webhookPath)
                        }
                    }
                }
            )
        }.shouldBeOK()

        localSessionManager.waitAllSessions()
        return sessionEndReportDeferred.await()
    }

    test("testSessionClaimAccumulation") {
        val sessionStartBudget = 1.dollars
        val claims = listOf(
            AgentBudgetUnit(1UL) to "minimum claim",
            10.cents to "10 cent claim",
        )

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claims.forEach { claim(it) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget - AgentBudgetUnit(claims.sumOf { it.first.value }))
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        sessionRunningBudget.claims.shouldHaveSize(2)

        sessionRunningBudget.claims[0].amount.shouldBeEqual(claims[0].first)
        sessionRunningBudget.claims[0].description.shouldBeEqual(claims[0].second)

        sessionRunningBudget.claims[1].amount.shouldBeEqual(claims[1].first)
        sessionRunningBudget.claims[1].description.shouldBeEqual(claims[1].second)
    }

    test("testSessionWarnedOverclaim") {
        val sessionStartBudget = 1.dollars
        val claimAmount = 2.dollars
        val claimDescription = "big claim"

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claim(claimAmount, claimDescription)
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(claimAmount - sessionStartBudget)

        sessionRunningBudget.claims.shouldHaveSize(1)
        sessionRunningBudget.claims[0].amount.shouldBeEqual(claimAmount)
        sessionRunningBudget.claims[0].description.shouldBeEqual(claimDescription)
    }

    test("testSessionKillAgent") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimDelay = 200.milliseconds
                    claims.forEach { claim(it) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent()
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        // the first claim of 1 dollar should cause the agent to exit, making no more claims
        sessionRunningBudget.claims.shouldHaveSize(1)
    }

    test("testSessionKillAgentIgnored") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claims.forEach { claim(it) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent()
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit(claims.sumOf { it.first.value }) - sessionStartBudget)

        // the agent ignores the shouldExit response and all 10 claims go through
        sessionRunningBudget.claims.shouldHaveSize(10)
    }

    test("testSessionKillAgentIgnoredWithForce") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claimDelay = 200.milliseconds
                    claims.forEach { claim(it) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent(force = true)
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        // the agent ignores the exit request, but the agent is forcefully killed by the server once the budget is
        // exhausted
        sessionRunningBudget.claims.shouldHaveSize(1)
    }

    test("testAgentBudgetNoSession") {
        val claimAmount = 1.dollars
        val agentClaimDescription = "agent claim"

        val sessionStartBudget = 1.dollars
        val agentStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claim(claimAmount, agentClaimDescription)
                    claim(2.dollars, "second claim")
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill()
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        sessionRunningBudget.claims.shouldBeEmpty()

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(agentStartBudget - claimAmount)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
    }

    test("testAgentBudgetWithSession") {
        val claimAmount = 1.dollars
        val agentClaimDescription = "agent claim"
        val sessionClaimDescription = "session claim"

        val sessionStartBudget = 1.dollars
        val agentStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claim(claimAmount, agentClaimDescription)
                    claim(claimAmount, sessionClaimDescription)
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget - claimAmount)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        sessionRunningBudget.claims.shouldHaveSize(1).first().should {
            it.amount.shouldBeEqual(claimAmount)
            it.description.shouldBeEqual(sessionClaimDescription)
        }

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(agentStartBudget - claimAmount)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        agentState.runningBudget.claims.shouldHaveSize(1).first().should {
            it.amount.shouldBeEqual(claimAmount)
            it.description.shouldBeEqual(agentClaimDescription)
        }
    }

    test("testAgentBudgetWithSessionSplit") {
        val claimAmount = 10.dollars
        val claimDescription = "agent claim"

        val sessionStartBudget = 3.dollars
        val agentStartBudget = 4.dollars

        claimAmount.shouldBeGreaterThan(sessionStartBudget + agentStartBudget)

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claim(claimAmount, claimDescription)
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(claimAmount - sessionStartBudget - agentStartBudget)

        sessionRunningBudget.claims.shouldHaveSize(1).first().should {
            // overclaimed!
            it.amount.shouldBeEqual(claimAmount - agentStartBudget)
            it.description.shouldBeEqual(claimDescription)
        }

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        agentState.runningBudget.claims.shouldHaveSize(1).first().should {
            it.amount.shouldBeEqual(claimAmount.coerceAtMost(agentStartBudget))
            it.description.shouldBeEqual(claimDescription)
        }
    }

    test("testMultiAgentClaimAccumulation") {
        val claimAmount = 1.dollars
        val claimDescription = "agent claim"

        val sessionStartBudget = 3.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claim(claimAmount, claimDescription)
                }
                claimAgent("agent2") {
                    claim(claimAmount, claimDescription)
                }
                claimAgent("agent3") {
                    claim(claimAmount, claimDescription)
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Warn
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
    }

    test("testMultiAgentCompeteForBudget") {
        val claimAmount = 1.dollars
        val claims = List(10) { claimAmount to "claim $it" }
        val sessionStartBudget = 10.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claims.forEach { claim(it) }
                    claimDelay = 100.milliseconds
                }
                claimAgent("agent2") {
                    claims.forEach { claim(it) }
                    claimDelay = 100.milliseconds
                }
                claimAgent("agent3") {
                    claims.forEach { claim(it) }
                    claimDelay = 100.milliseconds
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent()
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)

        // claiming is done with a mutex, so each claim is performed sequentially, it is possible that 3 agents
        // "queue" a claim as soon as one agent exhausts the budget, meaning the max overclaim in this scenario is 3
        // claims
        sessionRunningBudget.overclaim.shouldBeLessThanOrEqualTo(claimAmount * 3U)
    }

    test("testMultiAgentKillSessionExhaustionBehavior") {
        val claimAmount = 1.dollars
        val agentClaims = List(10) { claimAmount to "claim $it" }
        val sessionStartBudget = 1.dollars
        val agentStartBudget = claimAmount * agentClaims.size.toUInt()

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("chill agent") {
                    agentClaims.forEach { claim(it) }
                    claimDelay = 100.milliseconds
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill()
                    }
                }
                claimAgent("session killing agent") {
                    claimDelay = 200.milliseconds
                    claim(claimAmount * 2u, "killing claim")
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillSession(delay = Duration.ZERO)
            }
        }

        // session budget used exclusively by the session killing agent
        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(claimAmount)

        // the killing agent should kill the session, stopping the chill agent from claiming its own budget (as there is
        // a delay)
        val agentState = sessionState.agents.shouldHaveSize(2).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeGreaterThan(AgentBudgetUnit.ZERO)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
    }

    test("testSessionAccumulationMinimum") {
        val claims = List(100) { 1.dollars to "claim $it" }
        val sessionStartBudget = 1.dollars * claims.size.toUInt()
        val minimum = 10.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claims.forEach { claim(it) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent(minimum = minimum)
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(minimum)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
    }

    test("testAgentAccumulationMinimum") {
        val claims = List(100) { 1.dollars to "claim $it" }
        val agentStartBudget = 1.dollars * claims.size.toUInt()
        val minimum = 10.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claims.forEach { claim(it) }

                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill(minimum = minimum)
                    }
                }
            }
        }

        val agentRunningBudget =
            report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.agents.shouldHaveSize(1)
                .first().runningBudget

        agentRunningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentRunningBudget.remaining.shouldBeEqual(minimum)
        agentRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
    }
})