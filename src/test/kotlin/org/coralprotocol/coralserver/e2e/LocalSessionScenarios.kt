//package org.coralprotocol.coralserver.e2e
//
//import io.kotest.assertions.throwables.shouldNotThrowAny
//import kotlinx.coroutines.runBlocking
//import org.coralprotocol.coralserver.utils.UserMessage
//import org.coralprotocol.coralserver.utils.createConnectedKoogAgent
//import kotlin.test.Test
//import kotlin.uuid.ExperimentalUuidApi
//
//class LocalSessionScenarios {
//
//    @OptIn(ExperimentalUuidApi::class)
//    @Test
//    fun testTwoAgentConversationInSameThread(): Unit = runBlocking {
//        shouldNotThrowAny {
//            val server = TestCoralServer(port = 14392u, devmode = true).apply { setup() }
//
//            val session = server.sessionManager.getOrCreateSession("test", "aaa", "aaa", null)
//            val agent1 = createConnectedKoogAgent(server.server!!, "testAgent1", session = session)
//            val agent2 = createConnectedKoogAgent(server.server!!, "testAgent2", session = session)
//
//            session.waitForAgentCount(2, 2000)
//
//            agent1.step(UserMessage("Please create a new thread and tell testAgent2 the passcode is 3243."))
//
//            val sessions = server.sessionManager.getAllSessions()
//            assert(sessions.size == 1) { "There should be one session" }
//            val currentSession = sessions.first()
//            val threads = currentSession.getAllThreads()
//            assert(threads.size == 1) { "There should be one thread" }
//            assert(threads.first().messages.isNotEmpty()) { "There should be at least one message" }
//
//            val agent2Response =
//                agent2.step(UserMessage("What is the passcode testAgent1 just told you? Use wait_for_mentions to check."))
//            assert(agent2Response.content().contains("3243")) {
//                "Agent2 should receive the code from agent1. Got: ${agent2Response.content()}"
//            }
//
//            agent2.step(UserMessage("The passcode is 9920. Pass it to testAgent1 in the same thread."))
//
//            val agent1PasscodeFrom2Resp =
//                agent1.step(UserMessage("What is the passcode testAgent2 just told you? Use wait_for_mentions to check."))
//            assert(agent1PasscodeFrom2Resp.content().contains("9920")) {
//                "Agent1 should receive the right code from agent 2. Got: ${agent1PasscodeFrom2Resp.content()}"
//            }
//
//            assert(currentSession.getAllThreads().size == 1) { "There should still be only one thread" }
//        }
//    }
//}