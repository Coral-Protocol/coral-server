
package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
import java.util.*

class ExecutableRuntimeTest : FunSpec({
    test("testOptions").config(enabled = isWindows()) {
        sessionTest {
            val optionValue1 = UUID.randomUUID().toString()
            val optionValue2 = UUID.randomUUID().toString()

            val (session1, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = "agent1",
                                executableRuntime = ExecutableRuntime(listOf("does not exist"))
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE),
                        ),
                        graphAgent(
                            registryAgent = registryAgent(
                                name = "agent2",
                                executableRuntime = ExecutableRuntime(
                                    listOf(
                                        "powershell.exe", "-command", """
                                            write-output ${'$'}env:TEST_OPTION
                                            get-content ${'$'}env:TEST_FS_OPTION
                                        """.trimIndent()
                                    )
                                )
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE),
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

            // collect messages written to stdout by agent2
            val collecting = CompletableDeferred<Unit>()
            val messages = mutableListOf<String>()
            val agent2 = session1.getAgent("agent2")
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

            collector.cancelAndJoin()
        }
    }
})