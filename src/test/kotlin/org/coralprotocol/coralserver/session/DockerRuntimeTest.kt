package org.coralprotocol.coralserver.session

import DockerRuntime
import io.ktor.test.dispatcher.runTestWithRealTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.logging.LogMessage
import java.util.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DockerRuntimeTest : SessionBuilding() {
    @Test
    fun testOptions() = runTest {
        withContext(Dispatchers.IO) {
            val optionValue1 = UUID.randomUUID().toString()
            val optionValue2 = UUID.randomUUID().toString()

            val (session1, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = "agent1",
                                dockerRuntime = DockerRuntime(
                                    image = "ubuntu:latest",
                                    command = listOf("bash", "-c", """
                                        echo ${'$'}TEST_OPTION
                                        cat ${'$'}TEST_FS_OPTION
                                    """.trimIndent())
                                )
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.DOCKER),
                            options = mapOf(
                                "TEST_OPTION" to AgentOptionWithValue.String(
                                    option = AgentOption.String(),
                                    value = AgentOptionValue.String(optionValue1)
                                ),
                                "TEST_FS_OPTION" to AgentOptionWithValue.String(
                                    option = run {
                                        val opt = AgentOption.String()
                                        opt.transport = AgentOptionTransport.FILE_SYSTEM
                                        opt
                                    },
                                    value = AgentOptionValue.String(optionValue2)
                                )
                            )
                        ),
                    ),
                    customTools = mapOf(),
                    groups = setOf()
                )
            )

            // collect messages written to stdout by agent1
            val collecting = CompletableDeferred<Unit>()
            val messages = mutableListOf<String>()
            val agent1 = session1.getAgent("agent1")
            session1.sessionScope.launch {
                collecting.complete(Unit)
                agent1.logger.getSharedFlow().collect {
                    if (it is LogMessage.Info)
                        messages.add(it.message)
                }
            }

            // no exceptions should be thrown for agent1, run agent1 until it exits
            collecting.await()
            session1.launchAgents()
            session1.joinAgents()

            // Test that the script printed both env and fs option values
            assert(messages.contains(optionValue1))
            assert(messages.contains(optionValue2))
        }
    }

    @Test
    fun cleanupTest() = runTest {
        withContext(Dispatchers.IO) {
            val (session1, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = "agent1",
                                dockerRuntime = DockerRuntime(
                                    image = "ubuntu:latest",
                                    command = listOf("bash", "-c", """sleep 1000""".trimIndent())
                                )
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.DOCKER),
                            options = mapOf()
                        ),
                    ),
                    customTools = mapOf(),
                    groups = setOf()
                )
            )

            session1.launchAgents()
            withTimeoutOrNull(1.seconds) {
                session1.joinAgents()
            }
            session1.cancelAndJoinAgents()


            println("test exit" + session1.id)
        }
    }
}