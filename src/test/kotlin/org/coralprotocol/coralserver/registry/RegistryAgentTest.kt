package org.coralprotocol.coralserver.registry

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.AgentResolutionContext
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.koin.test.inject

class RegistryAgentTest : CoralTest({
    fun loadRegistryAgentFromResource(resourceName: String): RegistryAgent {
        val toml by inject<Toml>()
        val resource = javaClass.getResourceAsStream(resourceName).shouldNotBeNull()
        val registryAgent = toml.decodeFromNativeReader<UnresolvedRegistryAgent>(resource.reader())
        return registryAgent.resolve(AgentResolutionContext(AgentRegistrySourceIdentifier.Local))
    }

    fun verifyJson(registryAgent: RegistryAgent) {
        val json by inject<Json>()
        val jsonString = json.encodeToString(registryAgent)
        val recoded = json.decodeFromString<RegistryAgent>(jsonString)
        registryAgent.shouldBeEqual(recoded)
    }

    @Suppress("DEPRECATION")
    test("testEdition1") {
        val agent = loadRegistryAgentFromResource("/agent-edition1/coral-agent.toml")

        agent.edition.shouldBeEqual(1)
        agent.name.shouldBe("edition-1")
        agent.version.shouldBeEqual("0.1.0")

        agent.runtimes.executableRuntime.shouldNotBeNull().command.shouldContainExactly(
            "my-agent.exe",
            "--some-argument"
        )
        val dockerRuntime = agent.runtimes.dockerRuntime.shouldNotBeNull()
        dockerRuntime.image.shouldBeEqual("myuser/myimage")
        dockerRuntime.command.shouldBeNull()

        agent.runtimes.functionRuntime.shouldBeNull()

        val secret = agent.options["SECRET"].shouldNotBeNull()
        secret.description.shouldNotBeNull().shouldBeEqual("edition 1 secret type")
        secret.shouldBeInstanceOf<AgentOption.Secret>()

        val number = agent.options["NUMBER"].shouldNotBeNull()
        number.description.shouldNotBeNull().shouldBeEqual("edition 1 number type")
        number.shouldBeInstanceOf<AgentOption.Number>()

        val string = agent.options["STRING"].shouldNotBeNull()
        string.description.shouldNotBeNull().shouldBeEqual("edition 1 string type")
        string.shouldBeInstanceOf<AgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual("default value for the string type")

        verifyJson(agent)
    }

    test("testEdition2") {
        val agent = loadRegistryAgentFromResource("/agent-edition2/coral-agent.toml")
    }

    test("testEdition3") {
        val agent = loadRegistryAgentFromResource("/agent-edition3/coral-agent.toml")
    }
})