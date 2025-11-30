package org.coralprotocol.coralserver.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.junit.jupiter.api.Disabled
import java.util.UUID
import kotlin.test.Test

class ExecutableRuntimeTest : SessionBuilding() {
    @Test
    @Disabled("Requires Windows/PowerShell")
    fun testOptions() = runTest {
        withContext(Dispatchers.IO) {
            val uniqueValue = UUID.randomUUID().toString()

            val (session1, _) = sessionManager.createSession("test", AgentGraph(
                agents = mapOf(
                    "agent1" to graphAgent(
                        registryAgent = registryAgent(
                            executableRuntime = ExecutableRuntime(listOf("does not exist"))
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE),
                    ),
                    "agent2" to graphAgent(
                        registryAgent = registryAgent(
                            executableRuntime = ExecutableRuntime(listOf("powershell.exe", "-command", "write-output \$env:TEST_OPTION"))
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE),
                        options = mapOf("TEST_OPTION" to AgentOptionWithValue.String(
                            option = AgentOption.String(),
                            value = AgentOptionValue.String(uniqueValue)
                        ))
                    ),
                ),
                customTools = mapOf(),
                groups = setOf()
            ))

            // collect messages written to stdout by agent2
            val messages = mutableListOf<String>()
            val agent2 = session1.getAgent("agent2")
            session1.sessionScope.launch {
                agent2.logger.getSharedFlow().collect {
                    if (it is SessionAgentLogMessage.Info)
                        messages.add(it.message)
                }
            }

            // no exceptions should be thrown for agent1, run agent2 until it exits
            session1.launchAgents()

            // agent2 must have printed "test"
            assert(messages.contains(uniqueValue))
        }
    }
}