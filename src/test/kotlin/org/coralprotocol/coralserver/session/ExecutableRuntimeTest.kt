package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingEvent
import org.coralprotocol.coralserver.modules.LOGGER_TEST
import org.coralprotocol.coralserver.util.isWindows
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.shouldPostEvents
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.*
import kotlin.time.Duration.Companion.seconds

class ExecutableRuntimeTest : CoralTest({
    test("testOptions").config(enabled = isWindows()) {
        val localSessionManager by inject<LocalSessionManager>()
        val logger by inject<Logger>(named(LOGGER_TEST))

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

        shouldPostEvents(
            timeout = 3.seconds,
            allowUnexpectedEvents = true,
            events = mutableListOf(
                TestEvent("value 1") { it is LoggingEvent.Info && it.text == optionValue1 },
                TestEvent("value 2") { it is LoggingEvent.Info && it.text == optionValue2 },
                TestEvent("secret") { it is LoggingEvent.Info && it.text == unitTestSecret }
            ),
            logger.flow
        ) {
            session1.fullLifeCycle()
        }
    }
})