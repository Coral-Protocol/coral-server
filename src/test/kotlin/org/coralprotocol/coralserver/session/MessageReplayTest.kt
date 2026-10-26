package org.coralprotocol.coralserver.session

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.mcp.tools.WaitForMentioningMessageInput
import org.coralprotocol.coralserver.mcp.tools.waitForMentioningMessageExecutor
import org.coralprotocol.coralserver.util.utcTimeNow
import org.koin.test.inject
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MessageReplayTest : CoralTest({
    val timeout = 500.milliseconds.inWholeMilliseconds

    suspend fun createSession(): LocalSession {
        val localSessionManager by inject<LocalSessionManager>()
        return localSessionManager.createSession(
            UUID.randomUUID().toString(),
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                    graphAgentPair("agent3"),
                )
            )
        ).first
    }

    test("testSimultaneousMessagesBothReceived") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        // both agents see the same state, then decide to message each other at the same time
        val stateTime = utcTimeNow()
        agent1.sendMessage("hello from agent1", thread.id, setOf(agent2.name))
        agent2.sendMessage("hello from agent2", thread.id, setOf(agent1.name))

        // both messages were sent before either agent started waiting, without replay both waits would time out
        agent1.waitForMessage(timeoutMs = timeout).shouldBeNull()

        val wait1 = session.sessionScope.async {
            waitForMentioningMessageExecutor(agent1, WaitForMentioningMessageInput(stateTime, timeout)).message
        }
        val wait2 = session.sessionScope.async {
            waitForMentioningMessageExecutor(agent2, WaitForMentioningMessageInput(stateTime, timeout)).message
        }

        wait1.await().shouldNotBeNull().text.shouldBeEqual("hello from agent2")
        wait2.await().shouldNotBeNull().text.shouldBeEqual("hello from agent1")

        session.sessionScope.cancel()
    }

    test("testStateMarksReceivedMessages") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        val before = utcTimeNow()
        agent1.sendMessage("received", thread.id)
        delay(10.milliseconds)
        agent1.sendMessage("passive", thread.id)

        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout).shouldNotBeNull().text.shouldBeEqual("received")

        fun JsonObject.messages() = getValue("messages").jsonArray.map { it.jsonObject }
        fun JsonObject.text() = getValue("messageText").jsonPrimitive.content

        val agent2Messages = thread.asJsonState(agent2).messages()
        agent2Messages.first { it.text() == "received" }
            .getValue("receivedFromWaitingTool").jsonPrimitive.boolean.shouldBeTrue()
        agent2Messages.first { it.text() == "passive" }
            .getValue("receivedFromWaitingTool").jsonPrimitive.boolean.shouldBeFalse()

        // the sender's own messages are not marked
        thread.asJsonState(agent1).messages().forEach { it.containsKey("receivedFromWaitingTool").shouldBeFalse() }

        agent2.renderState().shouldContain("\"receivedFromWaitingTool\":true")

        session.sessionScope.cancel()
    }

    test("testOwnMessageNotReplayed") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        // the time an agent sees in its state, before it sends a message during the same iteration
        val stateTime = utcTimeNow()
        agent1.sendMessage("my own message", thread.id)

        agent1.waitForMessage(replayAfter = stateTime, timeoutMs = timeout).shouldBeNull()

        // the other participant still receives it
        agent2.waitForMessage(replayAfter = stateTime, timeoutMs = timeout)
            .shouldNotBeNull().text.shouldBeEqual("my own message")

        session.sessionScope.cancel()
    }

    test("testOwnMessageNotDeliveredLive") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        // an agent that sends a message while it is also waiting, e.g. with parallel tool calls
        val wait = session.sessionScope.async { agent1.waitForMessage(timeoutMs = timeout) }
        delay(50.milliseconds)
        agent1.sendMessage("my own message", thread.id)

        wait.await().shouldBeNull()
        session.sessionScope.cancel()
    }

    test("testReplayOrderAcrossThreads") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val agent3 = session.getAgent("agent3")
        val threadA = session.createThread("a", agent1.name, setOf(agent2.name))
        val threadB = session.createThread("b", agent3.name, setOf(agent2.name))

        val before = utcTimeNow()
        agent3.sendMessage("earlier, thread B", threadB.id)
        delay(10.milliseconds)
        agent1.sendMessage("later, thread A", threadA.id)

        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout)
            .shouldNotBeNull().text.shouldBeEqual("earlier, thread B")
        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout)
            .shouldNotBeNull().text.shouldBeEqual("later, thread A")

        session.sessionScope.cancel()
    }

    test("testReceivedMessageNotReplayedAgain") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        val before = utcTimeNow()
        agent1.sendMessage("hello", thread.id)

        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout)
            .shouldNotBeNull().text.shouldBeEqual("hello")

        // the same replay time again, the message was already received
        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout).shouldBeNull()

        // even when the wait time is far in the past
        agent2.waitForMessage(replayAfter = Instant.fromEpochMilliseconds(0), timeoutMs = timeout).shouldBeNull()

        session.sessionScope.cancel()
    }

    test("testLiveMessageNotReplayedAgain") {
        val session = createSession()
        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("t", agent1.name, setOf(agent2.name))

        val before = utcTimeNow()
        val wait = session.sessionScope.async { agent2.waitForMessage(timeoutMs = 1.seconds.inWholeMilliseconds) }
        delay(50.milliseconds)
        agent1.sendMessage("hello", thread.id)
        wait.await().shouldNotBeNull().text.shouldBeEqual("hello")

        agent2.waitForMessage(replayAfter = before, timeoutMs = timeout).shouldBeNull()

        session.sessionScope.cancel()
    }
})
