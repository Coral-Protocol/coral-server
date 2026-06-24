package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.DEBUG_CLAIM_NAME
import org.coralprotocol.coralserver.agent.graph.AgentBudgetExhaustionBehavior
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.dsl.SessionRequestBuilder
import org.coralprotocol.coralserver.dsl.cents
import org.coralprotocol.coralserver.dsl.dollars
import org.coralprotocol.coralserver.dsl.sessionRequest
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.session.reporting.SessionEndReport
import org.coralprotocol.coralserver.session.state.SessionState
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.shouldPostEvents
import org.koin.core.component.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget - AgentBudgetUnit(claims.sumOf { it.first.value }))
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        val receipts = report.sessionState.state.agentClaimReceipts
        receipts.shouldHaveSize(claims.size)

        for (i in claims.indices) {
            receipts[i].cost.shouldBeEqual(claims[i].first)
            receipts[i].claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claims[i].second)
        }
    }

    test("testSessionWarnedOverclaim") {
        val sessionStartBudget = 1.dollars
        val claimAmount = 2.dollars
        val claimDescription = "big claim"

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimBudgetUnit(claimAmount, claimDescription)
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
            }
        }

        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(claimAmount - sessionStartBudget)

        val receipts = report.sessionState.state.agentClaimReceipts
        receipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(claimAmount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claimDescription)
        }
    }

    test("testSessionKillAgent") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimDelay = 200.milliseconds
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
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
        report.sessionState.state.agentClaimReceipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(claims[0].first)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claims[0].second)
        }
    }

    test("testSessionKillAgentIgnored") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
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

        val receipts = report.sessionState.state.agentClaimReceipts

        // the agent ignores the shouldExit response and all 10 claims go through
        receipts.shouldHaveSize(claims.size)
        for (i in claims.indices) {
            receipts[i].cost.shouldBeEqual(claims[i].first)
            receipts[i].claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claims[i].second)
        }
    }

    test("testSessionKillAgentIgnoredWithForce") {
        val claims = List(10) { 1.dollars to "claim $it" }

        val sessionStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claimDelay = 200.milliseconds
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
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
        report.sessionState.state.agentClaimReceipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(claims[0].first)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claims[0].second)
        }
    }

    test("testAgentBudgetNoSession") {
        val agentClaimAmount = 1.dollars
        val agentClaimDescription = "agent claim"

        val sessionClaimAmount = 2.dollars
        val sessionClaimDescription = "session claim"

        val sessionStartBudget = 1.dollars
        val agentStartBudget = 1.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimBudgetUnit(agentClaimAmount, agentClaimDescription)
                    claimBudgetUnit(sessionClaimAmount, sessionClaimDescription)
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill()
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(agentStartBudget - agentClaimAmount)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        // only the agent claim should make it through
        sessionState.agentClaimReceipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(agentClaimAmount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(agentClaimDescription)
        }
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
                    claimBudgetUnit(claimAmount, agentClaimDescription)
                    claimBudgetUnit(claimAmount, sessionClaimDescription)
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(sessionStartBudget - claimAmount)
        sessionRunningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(agentStartBudget - claimAmount)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        sessionState.agentClaimReceipts.shouldHaveSize(2)
        sessionState.agentClaimReceipts[0].should {
            it.cost.shouldBeEqual(claimAmount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(agentClaimDescription)
        }
        sessionState.agentClaimReceipts[1].should {
            it.cost.shouldBeEqual(claimAmount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(sessionClaimDescription)
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
                    claimBudgetUnit(claimAmount, claimDescription)
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession
                    }
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
            }
        }

        val sessionState = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        val sessionRunningBudget = sessionState.runningBudget
        sessionRunningBudget.startBudget.shouldBeEqual(sessionStartBudget)
        sessionRunningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        sessionRunningBudget.overclaim.shouldBeEqual(claimAmount - sessionStartBudget - agentStartBudget)

        val agentState = sessionState.agents.shouldHaveSize(1).first()
        agentState.runningBudget.startBudget.shouldBeEqual(agentStartBudget)
        agentState.runningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
        agentState.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)

        sessionState.agentClaimReceipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(claimAmount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claimDescription)
        }
    }

    test("testMultiAgentClaimAccumulation") {
        val claimAmount = 1.dollars
        val claimDescription = "agent claim"

        val sessionStartBudget = 3.dollars

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimBudgetUnit(claimAmount, claimDescription)
                }
                claimAgent("agent2") {
                    claimBudgetUnit(claimAmount, claimDescription)
                }
                claimAgent("agent3") {
                    claimBudgetUnit(claimAmount, claimDescription)
                }
            }
            budgetSettings {
                budget = sessionStartBudget
                exhaustionBehavior = SessionBudgetExhaustionBehavior.Ignore
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
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                    claimDelay = 100.milliseconds
                }
                claimAgent("agent2") {
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                    claimDelay = 100.milliseconds
                }
                claimAgent("agent3") {
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
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
                    agentClaims.forEach { claimBudgetUnit(it.first, it.second) }
                    claimDelay = 100.milliseconds
                    budgetSettings {
                        budget = agentStartBudget
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill()
                    }
                }
                claimAgent("session killing agent") {
                    claimDelay = 200.milliseconds
                    claimBudgetUnit(claimAmount * 2u, "killing claim")
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
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
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
                    claims.forEach { claimBudgetUnit(it.first, it.second) }

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

    test("testKillSessionDelay") {
        val claimAmount = 1.dollars
        val agentClaimDelay = 100.milliseconds
        val delayFactor = 5

        val claims = List(100) { claimAmount to "claim $it" }

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claimDelay = agentClaimDelay
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                }
            }
            budgetSettings {
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillSession(
                    delay = agentClaimDelay * delayFactor,
                )
            }
        }

        // force kill should occur exactly on the 5th claim, a claim not making it through and 1 extra claim making it
        // through
        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.overclaim.shouldBeBetween(
            claimAmount * (delayFactor - 1).toUInt(),
            claimAmount * (delayFactor + 1).toUInt()
        )
    }

    test("testSessionKillAgentForceDelay") {
        val claimAmount = 1.dollars
        val agentClaimDelay = 100.milliseconds
        val delayFactor = 10

        val claims = List(100) { claimAmount to "claim $it" }

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claimDelay = agentClaimDelay
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                }
            }
            budgetSettings {
                exhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent(
                    force = true,
                    forceDelay = agentClaimDelay * delayFactor,
                )
            }
        }

        // force kill should occur exactly on the 5th claim, a claim not making it through and 1 extra claim making it
        // through
        val sessionRunningBudget = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.runningBudget
        sessionRunningBudget.overclaim.shouldBeBetween(
            claimAmount * (delayFactor - 1).toUInt(),
            claimAmount * (delayFactor + 1).toUInt()
        )
    }

    test("testAgentKillForceDelay") {
        val claimAmount = 1.dollars
        val agentClaimDelay = 100.milliseconds
        val delayFactor = 3

        val claims = List(100) { claimAmount to "claim $it" }

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    ignoreShouldExit = true
                    claimDelay = agentClaimDelay
                    claims.forEach { claimBudgetUnit(it.first, it.second) }
                    budgetSettings {
                        exhaustionBehavior = AgentBudgetExhaustionBehavior.Kill(
                            force = true,
                            forceDelay = agentClaimDelay * delayFactor,
                        )
                    }
                }
            }
        }

        // force kill should occur exactly on the 5th claim, a claim not making it through and 1 extra claim making it
        // through
        val agentRunningBudget =
            report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state.agents.shouldHaveSize(1)
                .first().runningBudget

        agentRunningBudget.overclaim.shouldBeBetween(
            claimAmount * (delayFactor - 1).toUInt(),
            claimAmount * (delayFactor + 1).toUInt()
        )
    }

    test("testNonDefaultClaimPrice") {
        val amount = 1.dollars
        val claimDescription = "singular 1 dollar claim"

        val report = sessionEndReport {
            agentGraphRequest {
                claimAgent("agent1") {
                    claimQuantity(1u, claimDescription)
                    budgetSettings {
                        claimTypeCost(DEBUG_CLAIM_NAME, amount)
                        budget = amount
                    }
                }
            }
        }

        val state = report.sessionState.shouldBeInstanceOf<SessionState.Extended>().state
        state.agents.shouldHaveSize(1).first().should {
            it.runningBudget.startBudget.shouldBeEqual(amount)
            it.runningBudget.remaining.shouldBeEqual(AgentBudgetUnit.ZERO)
            it.runningBudget.overclaim.shouldBeEqual(AgentBudgetUnit.ZERO)
        }

        state.agentClaimReceipts.shouldHaveSize(1).first().should {
            it.cost.shouldBeEqual(amount)
            it.claim.shouldBeInstanceOf<SessionAgentClaim.RpcClaim>().additionalDescription.shouldNotBeNull()
                .shouldBeEqual(claimDescription)
        }
    }

    test("testClaimEvents") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val amount = 1.dollars
        val claims = List(10) { amount to "claim $it" }
        val sessionBudget = amount * claims.size.toUInt()

        val sid = client.authenticatedPost(LocalSessions.Session()) {
            setBody(
                sessionRequest {
                    deferExecution()
                    agentGraphRequest {
                        claimAgent("agent1") {
                            claims.forEach { claimBudgetUnit(it.first, it.second) }
                        }
                    }
                    budgetSettings {
                        budget = sessionBudget
                    }
                }
            )
        }.shouldBeOK().body<SessionIdentifier>()

        val session = localSessionManager.getSession(sid.namespace, sid.sessionId)

        session.shouldPostEvents(
            timeout = 3.seconds,
            allowUnexpectedEvents = true,
            events = claims.mapIndexed { index, claim ->
                TestEvent<SessionEvent>(claim.second) {
                    if (it is SessionEvent.AgentBudgetClaim && it.claim is SessionAgentClaim.RpcClaim) {
                        // clue for this failing is quite bad...
                        it.result.fulfilledAmount == amount
                                && it.claim.additionalDescription == claim.second
                                && it.result.remainingSessionBudget == amount * (claims.size - index - 1).toUInt()
                                && it.result.claimId == index
                                && it.result.shouldExit == (index == claims.size - 1)
                    } else
                        false
                }
            }.toMutableList()
        ) {
            client.authenticatedPost(
                LocalSessions.Session.Existing(
                    namespace = sid.namespace,
                    sessionId = sid.sessionId
                )
            ) {
                setBody(SessionRuntimeSettings())
            }
        }
    }
})