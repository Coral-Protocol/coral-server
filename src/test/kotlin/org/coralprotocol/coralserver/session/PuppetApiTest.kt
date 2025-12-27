package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.PuppetDebugAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.routes.api.v1.Puppet
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

class PuppetApiTest : CoralTest({
    val agent1Name = "puppet1"
    val agent2Name = "puppet2"

    suspend fun puppetSession(
        localSessionManager: LocalSessionManager,
        client: HttpClient,
        body: suspend (Puppet.Agent, Puppet.Agent, LocalSession) -> Unit
    ) {
        val ns = Sessions.WithNamespace(namespace = "ns")
        val id: SessionIdentifier = client.authenticatedPost(ns) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(PuppetDebugAgent.identifier) {
                            name = agent1Name
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        }
                        agent(PuppetDebugAgent.identifier) {
                            name = agent2Name
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        }
                        groupAllAgents()
                    }
                }
            )
        }.body()

        body(
            Puppet.Agent(namespace = id.namespace, sessionId = id.sessionId, agentName = agent1Name),
            Puppet.Agent(namespace = id.namespace, sessionId = id.sessionId, agentName = agent2Name),
            localSessionManager.getSessions(ns.namespace).find { it.id == id.sessionId }.shouldNotBeNull()
        )
    }

    test("testPuppetFunctions").config(timeout = 10.seconds) {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        puppetSession(localSessionManager, client) { agent1, agent2, session ->
            // 1. create thread as agent1
            val threadRoute1 = Puppet.Agent.Thread(agent1)
            val threadRoute2 = Puppet.Agent.Thread(agent2)

            val createThreadResponse: CreateThreadOutput = client.authenticatedPost(threadRoute1) {
                setBody(
                    CreateThreadInput(
                        threadName = "test thread",
                        participantNames = setOf()
                    )
                )
            }.shouldBeOK().body()

            createThreadResponse.thread.creatorName.shouldBeEqual(agent1Name)
            createThreadResponse.thread.hasParticipant(agent2Name).shouldBeFalse()

            val threadId = createThreadResponse.thread.id
            val thread = shouldNotThrowAny { session.getThreadById(threadId) }

            // 2. agent agent2 to the thread
            client.authenticatedPost(Puppet.Agent.Thread.Participant(threadRoute1)) {
                setBody(
                    AddParticipantInput(
                        threadId = threadId,
                        participantName = agent2Name
                    )
                )
            }.shouldBeOK()

            thread.hasParticipant(agent2Name).shouldBeTrue()

            // 3. now that agent2 is a participant of the thread, send a message as agent2
            client.authenticatedPost(Puppet.Agent.Thread.Message(threadRoute2)) {
                setBody(
                    SendMessageInput(
                        threadId = threadId,
                        content = "test message",
                        mentions = setOf(agent1Name)
                    )
                )
            }.shouldBeOK()

            thread.withMessageLock {
                it.shouldHaveSingleElement { msg ->
                    msg.senderName == agent2Name && msg.mentionNames.contains(agent1Name)
                }
            }

            // 4. agent2 can now remove itself from the thread
            client.authenticatedDelete(Puppet.Agent.Thread.Participant(threadRoute2)) {
                setBody(
                    RemoveParticipantInput(
                        threadId = threadId,
                        participantName = agent2Name
                    )
                )
            }.shouldBeOK()

            thread.hasParticipant(agent2Name).shouldBeFalse()

            // 5. thread can now be closed
            client.authenticatedDelete(threadRoute1) {
                setBody(
                    CloseThreadInput(
                        threadId = threadId,
                        summary = "thread finished",
                    )
                )
            }.shouldBeOK()

            // 6. both agents can now cancel themselves (waitAllSessions would otherwise hang)
            client.authenticatedDelete(agent1).shouldBeOK()
            client.authenticatedDelete(agent2).shouldBeOK()

            localSessionManager.waitAllSessions()
        }
    }
})