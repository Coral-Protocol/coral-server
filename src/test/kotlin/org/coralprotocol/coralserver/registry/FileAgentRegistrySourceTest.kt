@file:OptIn(ExperimentalPathApi::class)

package org.coralprotocol.coralserver.registry

import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AGENT_FILE
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.CURRENT_AGENT_EDITION
import org.coralprotocol.coralserver.agent.registry.FileAgentRegistrySource
import org.koin.test.inject
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileAgentRegistrySourceTest : CoralTest({
    val agentVersion = "1.0.0"
    val humanActionTime = 150.milliseconds

    suspend fun withTempDir(body: suspend Path.(CoroutineScope) -> Unit) {
        val path = createTempDirectory()
        try {
            val scope = CoroutineScope(Job())

            path.body(scope)
            scope.cancel()
        } finally {
            path.deleteRecursively()
        }
    }

    suspend fun Path.writeAgent(
        name: String,
        path: String = "$name/$AGENT_FILE",
        delay: Duration = Duration.ZERO
    ): Path {
        val agentPath = resolve(path)

        var current = agentPath.root
        for (part in agentPath.parent) {
            current = current.resolve(part)
            if (!current.isDirectory()) {
                current.createDirectory()
                delay(delay)
            }
        }

        agentPath.writeText(
            """
            edition = $CURRENT_AGENT_EDITION
            
            [agent]
            name = "$name"
            version = "$agentVersion"
        """.trimIndent()
        )

        delay(delay)

        return agentPath
    }

    test("testInvalidPath") {
        val registry by inject<AgentRegistry>()
        val scope = CoroutineScope(Job())

        try {
            shouldNotThrowAny { FileAgentRegistrySource(registry, UUID.randomUUID().toString(), true, scope) }
            shouldNotThrowAny {
                FileAgentRegistrySource(
                    registry,
                    "${UUID.randomUUID()}/*/${UUID.randomUUID()}",
                    true,
                    scope
                )
            }
        } finally {
            scope.cancel()
        }
    }

    test("testBasicFile") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"

        withTempDir {
            writeAgent(agentName)

                FileAgentRegistrySource(registry, resolve(agentName).toString()).agents.shouldHaveSingleElement {
                it.name == agentName
            }
        }
    }

    test("testPattern") {
        val registry by inject<AgentRegistry>()
        val agentNames = listOf("agent1", "agent2", "agent3")

        // agents/agent1/coral-agent.toml
        // agents/agent2/coral-agent.toml
        // agents/agent3/coral-agent.toml
        withTempDir {
            resolve("agents").apply {
                writeAgent("agent4", "nested/agent4/$AGENT_FILE") // bad agent, nested
                agentNames.forEach { writeAgent(it) }
            }

            FileAgentRegistrySource(registry, toString() + "/agents/*").agents.map { it.name }
                .shouldContainExactly(agentNames)
        }

        // agents/agent1/data-files/coral-agent.toml
        // agents/agent2/data-files/coral-agent.toml
        // agents/agent3/data-files/coral-agent.toml
        withTempDir {
            resolve("agents").apply {
                agentNames.forEach { writeAgent(it, "$it/data-files/$AGENT_FILE") }
            }

            writeAgent("agent4", "agents/agent4/$AGENT_FILE") // bad agent, not nested in data-files

            FileAgentRegistrySource(registry, toString() + "/agents/*/data-files/").agents.map { it.name }
                .shouldContainAll(agentNames)
        }
    }

    test("testWatchDelete") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"

        withTempDir {
            val agentPath = writeAgent(agentName)
            val noise = resolve("$agentName/noise.txt").apply {
                writeText("noise")
            }

            val registrySource = FileAgentRegistrySource(registry, resolve(agentName).toString(), true, it)
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            noise.deleteExisting()
            agentPath.deleteExisting()

            eventually(3.seconds) {
                registrySource.agents.shouldBeEmpty()
            }
        }
    }

    test("testWatchUpdate") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"
        val newAgentName = "agent2"

        withTempDir {
            writeAgent(agentName)
            val noise = resolve("$agentName/noise.txt").apply {
                writeText("noise")
            }


            val registrySource = FileAgentRegistrySource(registry, resolve(agentName).toString(), true, it)
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            noise.writeText("irrelevant update")
            writeAgent(newAgentName, "$agentName/$AGENT_FILE")

            eventually(3.seconds) {
                registrySource.agents.shouldHaveSingleElement { agent ->
                    agent.name == newAgentName
                }
            }
        }
    }

    test("testWatchNewAgent") {
        val registry by inject<AgentRegistry>()
        val agentNames = listOf("agent1", "agent2", "agent3")

        withTempDir {
            val registrySource = FileAgentRegistrySource(registry, toString() + "/agents/*", true, it)
            registrySource.agents.shouldBeEmpty()

            resolve("agents").apply {
                writeAgent("agent4", "nested/agent4/$AGENT_FILE", humanActionTime)
                agentNames.forEach { agent -> writeAgent(agent) }
            }

            eventually(3.seconds) {
                registrySource.agents.map { agent -> agent.name }.shouldContainAll(agentNames)
            }
        }
    }

    test("testDeleteScan") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"

        repeat(10) { depth ->
            withTempDir {
                val root = resolve("root")
                root.resolve("nest/".repeat(depth)).apply {
                    val path = resolve("agents").apply {
                        writeAgent(agentName)
                    }

                    val source = FileAgentRegistrySource(registry, "$path/*", false, it)
                    source.agents.shouldHaveSingleElement { agent -> agent.name == agentName }

                    root.deleteRecursively()
                    source.scan()

                    source.agents.shouldBeEmpty()

                    path.writeAgent(agentName)
                    source.scan()
                    source.agents.shouldHaveSingleElement { agent -> agent.name == agentName }
                }
            }
        }
    }

    test("testModifyRename") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"
        val newAgentName = "agent2"

        withTempDir {
            writeAgent(agentName)

            val registrySource = FileAgentRegistrySource(registry, resolve(agentName).toString(), true, it)
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            writeAgent(newAgentName, "$agentName/$AGENT_FILE")

            eventually(3.seconds) {
                registrySource.agents.shouldHaveSingleElement { agent ->
                    agent.name == newAgentName
                }
            }
        }
    }

    test("testModifyHumanSyntaxError") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"

        withTempDir {
            val path = writeAgent(agentName)

            val registrySource = FileAgentRegistrySource(registry, resolve(agentName).toString(), true, it)
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            delay(humanActionTime)
            path.writeText("not valid toml")
            delay(humanActionTime)

            // reload should not delete the agent
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            // re-scan should delete the agent
            registrySource.scan()
            registrySource.agents.shouldBeEmpty()
        }
    }

    test("testWatchNestedHuman") {
        val registry by inject<AgentRegistry>()
        val agentName = "agent1"

        withTempDir {
            var nestedPath = this
            repeat(5) { nestedPath = nestedPath.resolve("nest") }

            val registrySource = FileAgentRegistrySource(registry, "$nestedPath/*", true, it)
            registrySource.agents.shouldBeEmpty()

            nestedPath.writeAgent(agentName, delay = humanActionTime)

            eventually(3.seconds) {
                registrySource.agents.shouldHaveSingleElement { agent ->
                    agent.name == agentName
                }
            }
        }
    }

    test("testWatchRenameFolderHuman") {
        val registry by inject<AgentRegistry>()

        withTempDir {
            val registrySource = FileAgentRegistrySource(registry, "$this/agents/*", true, it)
            registrySource.agents.shouldBeEmpty()

            resolve("agents").apply {
                createDirectory()

                repeat(5) { index ->
                    val newFolder = resolve("New Folder").createDirectory().toFile()
                    delay(humanActionTime)

                    val newName = resolve("agent$index").toFile()
                    newFolder.renameTo(newName)
                    delay(humanActionTime)

                    newName.toPath().apply {
                        writeAgent("agent$index", AGENT_FILE)
                    }
                }
            }

            eventually(3.seconds) {
                registrySource.agents.shouldHaveSize(5)
            }
        }
    }

    test("testWatchDuplicates") {
        val registry by inject<AgentRegistry>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"
        withTempDir {
            val registrySource = FileAgentRegistrySource(registry, "$this/*", true, it)
            registrySource.agents.shouldBeEmpty()

            writeAgent(agent1Name, delay = humanActionTime)
            writeAgent(agent1Name, "$agent2Name/$AGENT_FILE", delay = humanActionTime)

            eventually(3.seconds) { registrySource.agents.shouldHaveSize(1) }
            continually(3.seconds) { registrySource.agents.shouldHaveSize(1) }
        }
    }
})