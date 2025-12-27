package org.coralprotocol.coralserver.session

import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.core.component.inject


class ConcurrencyTest : CoralTest({
    test("testSessionThread") {
        val localSessionManager by inject<LocalSessionManager>()

        val iterations = 100
        val agents = buildList {
            repeat(iterations) {
                add(graphAgentPair("agent$it"))
            }
            add(graphAgentPair("admin"))
        }.toMap()

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = agents
            )
        )

        val admin = session.getAgent("admin")
        val thread = session.createThread("Test thread", admin.name, setOf())

        val participantsWrite = launch {
            repeat(iterations) {
                thread.addParticipant(admin, session.getAgent("agent$it"))
                yield()
            }
        }

        val participantsRead = launch {
            while (true) {
                thread.withParticipantLock {
                    for (p in it) {
                        if (p == "agent${iterations - 1}")
                            cancel()

                        yield()
                    }
                }
            }
        }

        // will throw ConcurrentModificationException if participants allow iteration at the same time as writing
        joinAll(participantsWrite, participantsRead)

        val messagesWrite = launch {
            repeat(iterations) {
                thread.addMessage("test message$it", admin, setOf())
                yield()
            }
        }

        val messagesRead = launch {
            while (true) {
                thread.withMessageLock {
                    for (p in it) {
                        if (p.text == "test message${iterations - 1}")
                            cancel()

                        yield()
                    }
                }
            }
        }

        // will throw ConcurrentModificationException if messages allow iteration at the same time as writing
        joinAll(messagesWrite, messagesRead)
    }
})