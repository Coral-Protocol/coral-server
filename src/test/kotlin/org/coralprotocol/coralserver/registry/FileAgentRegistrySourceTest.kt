@file:OptIn(ExperimentalPathApi::class)

package org.coralprotocol.coralserver.registry

import io.kotest.assertions.eq.EqResolver.resolve
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSingleElement
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AGENT_FILE
import org.coralprotocol.coralserver.agent.registry.CURRENT_AGENT_EDITION
import org.coralprotocol.coralserver.agent.registry.FileAgentRegistrySource
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

class FileAgentRegistrySourceTest : CoralTest({
    fun withTempDir(body: Path.() -> Unit) {
        val path = createTempDirectory()
        try {
            path.body()
        } finally {
            path.deleteRecursively()
        }
    }

    fun Path.writeAgent(name: String, path: String = "$name/$AGENT_FILE") {
        val next = resolve(path)
        next.parent.toFile().mkdirs()
        next.writeText(
            """
            edition = $CURRENT_AGENT_EDITION
            
            [agent]
            name = "$name"
            version = "1.0.0"
        """.trimIndent()
        )
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

            FileAgentRegistrySource(toString() + "/agents/*/data-files/").agents.map { it.name }.shouldContainAll(agentNames)
        }
    }
})