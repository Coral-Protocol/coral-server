package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
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
                    claim(claimAmount to claimDescription)
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
})