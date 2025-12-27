package org.coralprotocol.coralserver.session

import io.kotest.matchers.collections.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.logging.LogMessage
import org.coralprotocol.coralserver.util.isWindows
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.core.component.inject
import java.util.*

class ExecutableRuntimeTest : CoralTest({
    test("testOptions").config(enabled = isWindows()) {
        val localSessionManager by inject<LocalSessionManager>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"

        val optionValue1 = UUID.randomUUID().toString()
        val optionValue2 = UUID.randomUUID().toString()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(ExecutableRuntime(listOf("does not exist")))
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(
                                ExecutableRuntime(
                                    listOf(
                                        "powershell.exe", "-command", """
                                            write-output TEST_OPTION:
                                            write-output ${'$'}env:TEST_OPTION

                                            write-output UNIT_TEST_SECRET:
                                            write-output ${'$'}env:UNIT_TEST_SECRET

                                            write-output TEST_FS_OPTION:
                                            get-content ${'$'}env:TEST_FS_OPTION
                                        """.trimIndent()
                                    )
                                )
                            )
                        }
                        option(
                            "TEST_OPTION", AgentOptionWithValue.String(
                                option = AgentOption.String(),
                                value = AgentOptionValue.String(optionValue1)
                            )
                        )
                        option(
                            "TEST_FS_OPTION", AgentOptionWithValue.String(
                                option = run {
                                    val opt = AgentOption.String()
                                    opt.transport = AgentOptionTransport.FILE_SYSTEM
                                    opt
                                },
                                value = AgentOptionValue.String(optionValue2)
                            )
                        )
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE)
                    }
                )
            )
        )

        // collect messages written to stdout by agent2
        val collecting = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        val agent2 = session1.getAgent(agent2Name)
        val collector = session1.sessionScope.launch {
            collecting.complete(Unit)
            agent2.logger.getSharedFlow().collect {
                if (it is LogMessage.Info)
                    messages.add(it.message)
            }
        }

        // no exceptions should be thrown for agent1, run agent2 until it exits
        collecting.await()
        session1.fullLifeCycle()

        // Test that the script printed both env and fs option values
        messages.shouldContain(optionValue1)
        messages.shouldContain(optionValue2)

        messages.shouldContain(unitTestSecret)

        collector.cancelAndJoin()
    }
})