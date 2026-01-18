@file:OptIn(ExperimentalPathApi::class)

package org.coralprotocol.coralserver.registry

import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AGENT_FILE
import org.coralprotocol.coralserver.agent.registry.CURRENT_AGENT_EDITION
import org.coralprotocol.coralserver.agent.registry.FileAgentRegistrySource
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileAgentRegistrySourceTest : CoralTest({
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
            version = "1.0.0"
        """.trimIndent()
        )

        return agentPath
    }

    test("testInvalidPath") {
        val scope = CoroutineScope(Job())

        try {
            shouldNotThrowAny { FileAgentRegistrySource(UUID.randomUUID().toString(), true, scope) }
            shouldNotThrowAny { FileAgentRegistrySource("${UUID.randomUUID()}/*/${UUID.randomUUID()}", true, scope) }
        } finally {
            scope.cancel()
        }
    }

    test("testBasicFile") {
        val agentName = "agent1"
        withTempDir {
            writeAgent(agentName)

            FileAgentRegistrySource(resolve(agentName).toString()).agents.shouldHaveSingleElement {
                it.name == agentName
            }
        }
    }

    test("testPattern") {
        val agentNames = listOf("agent1", "agent2", "agent3")

        // agents/agent1/coral-agent.toml
        // agents/agent2/coral-agent.toml
        // agents/agent3/coral-agent.toml
        withTempDir {
            resolve("agents").apply {
                writeAgent("agent4", "nested/agent4/$AGENT_FILE") // bad agent, nested
                agentNames.forEach { writeAgent(it) }
            }

            FileAgentRegistrySource(toString() + "/agents/*").agents.map { it.name }.shouldContainExactly(agentNames)
        }

        // agents/agent1/data-files/coral-agent.toml
        // agents/agent2/data-files/coral-agent.toml
        // agents/agent3/data-files/coral-agent.toml
        withTempDir {
            resolve("agents").apply {
                agentNames.forEach { writeAgent(it, "$it/data-files/$AGENT_FILE") }
            }

            writeAgent("agent4", "agents/agent4/$AGENT_FILE") // bad agent, not nested in data-files

            FileAgentRegistrySource(toString() + "/agents/*/data-files/").agents.map { it.name }
                .shouldContainAll(agentNames)
        }
    }

    test("testWatchDelete") {
        val agentName = "agent1"
        withTempDir {
            val agentPath = writeAgent(agentName)
            val noise = resolve("$agentName/noise.txt").apply {
                writeText("noise")
            }

            val registrySource = FileAgentRegistrySource(resolve(agentName).toString(), true, it)
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
        val agentName = "agent1"
        val newAgentName = "agent2"
        withTempDir {
            writeAgent(agentName)
            val noise = resolve("$agentName/noise.txt").apply {
                writeText("noise")
            }


            val registrySource = FileAgentRegistrySource(resolve(agentName).toString(), true, it)
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
        val agentNames = listOf("agent1", "agent2", "agent3")

        withTempDir {
            val registrySource = FileAgentRegistrySource(toString() + "/agents/*", true, it)
            registrySource.agents.shouldBeEmpty()

            resolve("agents").apply {
                writeAgent("agent4", "nested/agent4/$AGENT_FILE", 500.milliseconds)
                agentNames.forEach { agent -> writeAgent(agent) }
            }

            eventually(3.seconds) {
                registrySource.agents.map { agent -> agent.name }.shouldContainAll(agentNames)
            }
        }
    }

    test("testDeleteScan") {
        val agentName = "agent1"
        repeat(10) { depth ->
            withTempDir {
                val root = resolve("root")
                root.resolve("nest/".repeat(depth)).apply {
                    val path = resolve("agents").apply {
                        writeAgent(agentName)
                    }

                    val source = FileAgentRegistrySource("$path/*", false, it)
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

    test("testModifySyntaxError") {
        val agentName = "agent1"
        withTempDir {
            val path = writeAgent(agentName)

            val registrySource = FileAgentRegistrySource(resolve(agentName).toString(), true, it)
            registrySource.agents.shouldHaveSingleElement { agent ->
                agent.name == agentName
            }

            path.writeText("not valid toml")

            continually(1.seconds) {
                registrySource.agents.shouldHaveSingleElement { agent ->
                    agent.name == agentName
                }
            }
        }
    }

    test("testWatchNestedHuman") {
        val agentName = "agent1"
        withTempDir {
            var nestedPath = this
            repeat(5) { nestedPath = nestedPath.resolve("nest") }

            val registrySource = FileAgentRegistrySource("$nestedPath/*", true, it)
            registrySource.agents.shouldBeEmpty()

            nestedPath.writeAgent(agentName, delay = 200.milliseconds)

            eventually(1.seconds) {
                registrySource.agents.shouldHaveSingleElement { agent ->
                    agent.name == agentName
                }
            }
        }
    }
})