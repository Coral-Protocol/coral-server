package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.coralprotocol.coralserver.agent.debug.PuppetDebugAgent
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.routes.api.v1.Puppet
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest
import kotlin.time.Duration.Companion.seconds

class PuppetApiTest : FunSpec({
    val agent1Name = "puppet1"
    val agent2Name = "puppet2"

    suspend fun SessionTestScope.puppetSession(body: suspend (Puppet.Agent, Puppet.Agent, LocalSession) -> Unit) {
        val ns = Sessions.WithNamespace(namespace = "ns")
        val id: SessionIdentifier = ktor.client.post(ns) {
            withAuthToken()
            contentType(ContentType.Application.Json)
            setBody(
                SessionRequest(
                    agentGraphRequest = AgentGraphRequest(
                        agents = listOf(
                            GraphAgentRequest(
                                id = PuppetDebugAgent.identifier,
                                name = agent1Name,
                                description = "",
                                provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                            ),
                            GraphAgentRequest(
                                id = PuppetDebugAgent.identifier,
                                name = agent2Name,
                                description = "",
                                provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                            )
                        ),
                        groups = setOf(setOf(agent1Name, agent2Name)),
                    )
                )
            )
        }.body()

        body(
            Puppet.Agent(namespace = id.namespace, sessionId = id.sessionId, agentName = agent1Name),
            Puppet.Agent(namespace = id.namespace, sessionId = id.sessionId, agentName = agent2Name),
            sessionManager.getSessions(ns.namespace).find { it.id == id.sessionId }.shouldNotBeNull()
        )
    }

    test("testPuppetFunctions").config(timeout = 10.seconds) {
        sessionTest({
            addLocalAgents(
                listOf(
                    PuppetDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            puppetSession { agent1, agent2, session ->
                // 1. create thread as agent1
                val threadRoute1 = Puppet.Agent.Thread(agent1)
                val threadRoute2 = Puppet.Agent.Thread(agent2)

                val createThreadResponse: CreateThreadOutput = ktor.client.post(threadRoute1) {
                    withAuthToken()
                    contentType(ContentType.Application.Json)
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
                ktor.client.post(Puppet.Agent.Thread.Participant(threadRoute1)) {
                    withAuthToken()
                    contentType(ContentType.Application.Json)
                    setBody(
                        AddParticipantInput(
                            threadId = threadId,
                            participantName = agent2Name
                        )
                    )
                }.shouldBeOK()

                thread.hasParticipant(agent2Name).shouldBeTrue()

                // 3. now that agent2 is a participant of the thread, send a message as agent2
                ktor.client.post(Puppet.Agent.Thread.Message(threadRoute2)) {
                    withAuthToken()
                    contentType(ContentType.Application.Json)
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
                ktor.client.delete(Puppet.Agent.Thread.Participant(threadRoute2)) {
                    withAuthToken()
                    contentType(ContentType.Application.Json)
                    setBody(
                        RemoveParticipantInput(
                            threadId = threadId,
                            participantName = agent2Name
                        )
                    )
                }.shouldBeOK()

                thread.hasParticipant(agent2Name).shouldBeFalse()

                // 5. thread can now be closed
                ktor.client.delete(threadRoute1) {
                    withAuthToken()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CloseThreadInput(
                            threadId = threadId,
                            summary = "thread finished",
                        )
                    )
                }.shouldBeOK()

                // 6. both agents can now cancel themselves (waitAllSessions would otherwise hang)
                ktor.client.delete(agent1) {
                    withAuthToken()
                }.shouldBeOK()

                ktor.client.delete(agent2) {
                    withAuthToken()
                }.shouldBeOK()

                sessionManager.waitAllSessions()
            }
        }
    }
})