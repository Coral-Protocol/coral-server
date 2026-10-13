package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.PUPPET_AGENT_IDENTIFIER
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.dsl.sessionRequest
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.routes.api.v1.Puppet
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

/**
 * PuppetApiTest only sends a Text part through this REST route, a JSON path independent
 * of the MCP tool's own handling of the same type. This round trips all five kinds
 * through the real HTTP boundary instead.
 */
class PuppetApiRichContentTest : CoralTest({
    val agent1Name = "puppet1"
    val agent2Name = "puppet2"
    val namespaceName = "default"

    suspend fun puppetSession(
        localSessionManager: LocalSessionManager,
        client: HttpClient,
        body: suspend (Puppet.Agent, Puppet.Agent, LocalSession) -> Unit
    ) {
        val id: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(
                sessionRequest {
                    createNamespaceIfNotExists {
                        name = namespaceName
                    }
                    agentGraphRequest {
                        agent(PUPPET_AGENT_IDENTIFIER) {
                            name = agent1Name
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        }
                        agent(PUPPET_AGENT_IDENTIFIER) {
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
            localSessionManager.getSessions(namespaceName).find { it.id == id.sessionId }.shouldNotBeNull()
        )
    }

    test("testPuppetSendMessageRichContentRoundTrip").config(invocationTimeout = 10.seconds) {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        puppetSession(localSessionManager, client) { agent1, agent2, session ->
            val threadRoute1 = Puppet.Agent.Thread(agent1)

            val createThreadResponse: CreateThreadOutput = client.authenticatedPost(threadRoute1) {
                setBody(
                    CreateThreadInput(
                        threadName = "rich content thread",
                        participantNames = listOf()
                    )
                )
            }.shouldBeOK().body()

            val threadId = createThreadResponse.thread.id
            val thread = session.getThreadById(threadId)

            // Every content kind plus text, through the REST route's own JSON handling,
            // independent of the MCP tool's handling of the same type.
            val sentContent = listOf(
                SessionThreadMessagePart.Text("hello"),
                SessionThreadMessagePart.Image(data = "aGVsbG8taW1hZ2U=", mimeType = "image/png"),
                SessionThreadMessagePart.Audio(data = "aGVsbG8tYXVkaW8=", mimeType = "audio/wav"),
                SessionThreadMessagePart.EmbeddedResource(
                    resource = ResourceContents.Text(
                        text = "embedded text resource",
                        uri = "resource://thing/text",
                        mimeType = "text/plain"
                    )
                ),
                SessionThreadMessagePart.EmbeddedResource(
                    resource = ResourceContents.Blob(
                        blob = "aGVsbG8tYmxvYg==",
                        uri = "resource://thing/blob",
                        mimeType = null
                    )
                ),
                SessionThreadMessagePart.ResourceLink(
                    uri = "resource://thing/link",
                    name = "linked-thing",
                    mimeType = "application/json",
                    title = "A linked thing",
                    description = "Exercises every optional field of ResourceLink"
                ),
                // optional fields omitted entirely, to confirm they decode back to null
                SessionThreadMessagePart.ResourceLink(
                    uri = "resource://thing/bare-link",
                    name = "bare-linked-thing"
                ),
            )

            val sendMessageOutput: SendMessageOutput = client.authenticatedPost(Puppet.Agent.Thread.Message(threadRoute1)) {
                setBody(
                    SendMessageInput(
                        threadId = threadId,
                        content = sentContent,
                        mentions = listOf()
                    )
                )
            }.shouldBeOK().body()

            // the HTTP response, decoded back through Ktor ContentNegotiation, must preserve every part exactly
            // (kind, field values, and order)
            sendMessageOutput.message.content.shouldContainExactly(sentContent)

            // the durable, server-side stored message (independent of what the response claims) must match too
            thread.withMessageLock { messages ->
                val stored = messages.single { it.id == sendMessageOutput.message.id }
                stored.content.shouldContainExactly(sentContent)
            }

            // cleanup so waitAllSessions does not hang
            client.authenticatedDelete(agent1).shouldBeOK()
            client.authenticatedDelete(agent2).shouldBeOK()

            localSessionManager.waitAllSessions()
        }
    }
})
