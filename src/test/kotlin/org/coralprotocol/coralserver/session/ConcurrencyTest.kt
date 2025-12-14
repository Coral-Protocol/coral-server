package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.coralprotocol.coralserver.agent.graph.AgentGraph

class ConcurrencyTest : FunSpec({
    test("testSessionThread") {
        sessionTest {
            val iterations = 100
            val agents = buildList {
                repeat(iterations) {
                    add(graphAgent("agent$it"))
                }
                add(graphAgent("admin"))
            }.toMap()

            val (session, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = agents,
                    customTools = mapOf(),
                    groups = setOf()
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
    }
})