package org.coralprotocol.coralserver.registry

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.decodeFromNativeReader
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.koin.test.inject

class RegistryAgentTest : CoralTest({
    fun loadRegistryAgentFromResource(resourceName: String): RegistryAgent {
        val toml by inject<Toml>()
        val resource = javaClass.getResourceAsStream(resourceName).shouldNotBeNull()
        val registryAgent = toml.decodeFromNativeReader<UnresolvedRegistryAgent>(resource.reader())
        return registryAgent.resolve(AgentResolutionContext(AgentRegistrySourceIdentifier.Local))
    }

    fun testJsonRecode(agent: RegistryAgent) {
        val json by inject<Json>()
        val jsonString = json.encodeToString(agent)
        val recoded = json.decodeFromString<RegistryAgent>(jsonString)
        agent.shouldBeEqual(recoded)
    }

    fun testAgentHeader(agent: RegistryAgent) {
        agent.edition.shouldBeEqual(3)
        agent.name.shouldBe("edition-3")
        agent.version.shouldBeEqual("0.3.0")
        agent.capabilities.shouldContainAll(AgentCapability.TOOL_REFRESHING, AgentCapability.RESOURCES)
    }

    fun testAgentRuntimes(agent: RegistryAgent) {
        agent.runtimes.functionRuntime.shouldBeNull()

        val dockerRuntime = agent.runtimes.dockerRuntime.shouldNotBeNull()
        val executableRuntime = agent.runtimes.executableRuntime.shouldNotBeNull()

        dockerRuntime.image.shouldBeEqual("myuser/myimage")

        executableRuntime.path.shouldBeEqual("my-agent")
        executableRuntime.arguments.shouldContainExactly("--some-argument")
    }

    fun testOptions(agent: RegistryAgent) {
        val fullStringOption =
            agent.options["FULL_STRING_OPTION"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.String>()

        fullStringOption.required.shouldBeTrue()
        fullStringOption.transport.shouldBe(AgentOptionTransport.FILE_SYSTEM)

        val fullStringOptionDisplay = fullStringOption.display.shouldNotBeNull()
        fullStringOptionDisplay.label.shouldNotBeNull().shouldBeEqual("Full string option")
        fullStringOptionDisplay.description.shouldNotBeNull()
            .shouldBeEqual("An example of a string type option with every field configured")
        fullStringOptionDisplay.group.shouldNotBeNull().shouldBeEqual("Full options")
        fullStringOptionDisplay.multiline.shouldBeTrue()

        val defaultI8 = agent.options["DEFAULT_I8"].shouldNotBeNull()
        defaultI8.shouldBeInstanceOf<AgentOption.Byte>().default.shouldNotBeNull()
            .shouldBeEqual(-42)

        val defaultI16 = agent.options["DEFAULT_I16"].shouldNotBeNull()
        defaultI16.shouldBeInstanceOf<AgentOption.Short>().default.shouldNotBeNull()
            .shouldBeEqual(1024)

        val defaultI32 = agent.options["DEFAULT_I32"].shouldNotBeNull()
        defaultI32.shouldBeInstanceOf<AgentOption.Int>().default.shouldNotBeNull()
            .shouldBeEqual(123456)

        val defaultI64 = agent.options["DEFAULT_I64"].shouldNotBeNull()
        defaultI64.shouldBeInstanceOf<AgentOption.Long>().default.shouldNotBeNull()
            .shouldBeEqual(9876543210L)

        val defaultU8 = agent.options["DEFAULT_U8"].shouldNotBeNull()
        defaultU8.shouldBeInstanceOf<AgentOption.UByte>().default.shouldNotBeNull()
            .shouldBeEqual(200u)

        val defaultU16 = agent.options["DEFAULT_U16"].shouldNotBeNull()
        defaultU16.shouldBeInstanceOf<AgentOption.UShort>().default.shouldNotBeNull()
            .shouldBeEqual(5000u)

        val defaultU32 = agent.options["DEFAULT_U32"].shouldNotBeNull()
        defaultU32.shouldBeInstanceOf<AgentOption.UInt>().default.shouldNotBeNull()
            .shouldBeEqual(1000000u)

        val defaultU64 = agent.options["DEFAULT_U64"].shouldNotBeNull()
        defaultU64.shouldBeInstanceOf<AgentOption.ULong>().default.shouldNotBeNull()
            .shouldBeEqual("18446744073709")

        val defaultF32 = agent.options["DEFAULT_F32"].shouldNotBeNull()
        defaultF32.shouldBeInstanceOf<AgentOption.Float>().default.shouldNotBeNull()
            .shouldBeEqual(3.14f)

        val defaultF64 = agent.options["DEFAULT_F64"].shouldNotBeNull()
        defaultF64.shouldBeInstanceOf<AgentOption.Double>().default.shouldNotBeNull()
            .shouldBeEqual(2.718281828)

        val defaultBool = agent.options["DEFAULT_BOOL"].shouldNotBeNull()
        defaultBool.shouldBeInstanceOf<AgentOption.Boolean>().default.shouldNotBeNull()
            .shouldBeEqual(true)

        val defaultString = agent.options["DEFAULT_STRING"].shouldNotBeNull()
        defaultString.shouldBeInstanceOf<AgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual("hello world")

        val blobText = "hello world"
        val defaultBlob = agent.options["DEFAULT_BLOB"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Blob>()
        defaultBlob.default.shouldNotBeNull().shouldBeEqual(blobText.encodeBase64())
        defaultBlob.defaultBytes.shouldNotBeNull().toList().shouldContainExactly(blobText.toByteArray().toList())

        val defaultListI8 = agent.options["DEFAULT_LIST_I8"].shouldNotBeNull()
        defaultListI8.shouldBeInstanceOf<AgentOption.ByteList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf<Byte>(-1, 0, 1, 127))

        val defaultListI16 = agent.options["DEFAULT_LIST_I16"].shouldNotBeNull()
        defaultListI16.shouldBeInstanceOf<AgentOption.ShortList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf<Short>(100, 200, 300))

        val defaultListI32 = agent.options["DEFAULT_LIST_I32"].shouldNotBeNull()
        defaultListI32.shouldBeInstanceOf<AgentOption.IntList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(1000, 2000, 3000))

        val defaultListI64 = agent.options["DEFAULT_LIST_I64"].shouldNotBeNull()
        defaultListI64.shouldBeInstanceOf<AgentOption.LongList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(1000000L, 2000000L))

        val defaultListU8 = agent.options["DEFAULT_LIST_U8"].shouldNotBeNull()
        defaultListU8.shouldBeInstanceOf<AgentOption.UByteList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf<UByte>(1u, 2u, 3u, 255u))

        val defaultListU16 = agent.options["DEFAULT_LIST_U16"].shouldNotBeNull()
        defaultListU16.shouldBeInstanceOf<AgentOption.UShortList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf<UShort>(500u, 1000u, 1500u))

        val defaultListU32 = agent.options["DEFAULT_LIST_U32"].shouldNotBeNull()
        defaultListU32.shouldBeInstanceOf<AgentOption.UIntList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(10000u, 20000u))

        val defaultListU64 = agent.options["DEFAULT_LIST_U64"].shouldNotBeNull()
        defaultListU64.shouldBeInstanceOf<AgentOption.ULongList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf("100000", "200000", "300000"))

        val defaultListF32 = agent.options["DEFAULT_LIST_F32"].shouldNotBeNull()
        defaultListF32.shouldBeInstanceOf<AgentOption.FloatList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(1.1f, 2.2f, 3.3f))

        val defaultListF64 = agent.options["DEFAULT_LIST_F64"].shouldNotBeNull()
        defaultListF64.shouldBeInstanceOf<AgentOption.DoubleList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(1.414, 1.732, 2.236))

        val defaultListString = agent.options["DEFAULT_LIST_STRING"].shouldNotBeNull()
        defaultListString.shouldBeInstanceOf<AgentOption.StringList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf("foo", "bar", "baz"))

        val blobs = listOf("hello", "world")
        val defaultListBlob =
            agent.options["DEFAULT_LIST_BLOB"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.BlobList>()
        defaultListBlob.default.shouldContainExactly(blobs.map { it.encodeBase64() })
        defaultListBlob.defaultBytes.shouldContainExactly(blobs.map { it.toByteArray().toList() })

        agent.options["OPTIONAL_I8"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Byte>()
        agent.options["OPTIONAL_I16"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Short>()
        agent.options["OPTIONAL_I32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Int>()
        agent.options["OPTIONAL_I64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Long>()
        agent.options["OPTIONAL_U8"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UByte>()
        agent.options["OPTIONAL_U16"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UShort>()
        agent.options["OPTIONAL_U32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UInt>()
        agent.options["OPTIONAL_U64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.ULong>()
        agent.options["OPTIONAL_F32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Float>()
        agent.options["OPTIONAL_F64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Double>()
        agent.options["OPTIONAL_BOOL"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Boolean>()
        agent.options["OPTIONAL_STRING"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.String>()
        agent.options["OPTIONAL_BLOB"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Blob>()
        agent.options["OPTIONAL_LIST_I8"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.ByteList>()
        agent.options["OPTIONAL_LIST_I16"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.ShortList>()
        agent.options["OPTIONAL_LIST_I32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.IntList>()
        agent.options["OPTIONAL_LIST_I64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.LongList>()
        agent.options["OPTIONAL_LIST_U8"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UByteList>()
        agent.options["OPTIONAL_LIST_U16"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UShortList>()
        agent.options["OPTIONAL_LIST_U32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UIntList>()
        agent.options["OPTIONAL_LIST_U64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.ULongList>()
        agent.options["OPTIONAL_LIST_F32"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.FloatList>()
        agent.options["OPTIONAL_LIST_F64"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.DoubleList>()
        agent.options["OPTIONAL_LIST_STRING"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.StringList>()
        agent.options["OPTIONAL_LIST_BLOB"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.BlobList>()
    }

    fun testMarketplace(agent: RegistryAgent) {
        val marketplace = agent.marketplace.shouldNotBeNull()
        marketplace.readme.shouldBeEqual("A full markdown markdown readme for the agent on the marketplace")
        marketplace.summary.shouldBeEqual("A short NON-markdown summary of the agent on the marketplace")
        marketplace.license.shouldNotBeNull().shouldBeEqual("example license")

        marketplace.links.shouldBeEqual(
            mapOf(
                "github" to "https://github.com/coral-Protocol/coral-server",
                "website" to "https://www.coralos.ai/"
            )
        )

        val pricing = marketplace.pricing.shouldNotBeNull()

        pricing.description.shouldBeEqual("A full markdown description of how the agent is priced")
        pricing.currency.shouldBeEqual("USD")
        pricing.recommendations.min.shouldBeEqual(0.01)
        pricing.recommendations.max.shouldBeEqual(1.5)

        val identities = marketplace.identities.shouldNotBeNull()
        val erc8004 = identities.erc8004.shouldNotBeNull()

        erc8004.wallet.shouldBeEqual("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        erc8004.endpoints.shouldContainExactly(
            listOf(
                Erc8004Endpoint("first_endpoint", "https://api.my-server.com/first"),
                Erc8004Endpoint("second-endpoint", "https://api.my-server.com/second")
            )
        )
    }

    test("testRegistryAgent") {
        val agent = loadRegistryAgentFromResource("/agent/coral-agent.toml")

        testAgentHeader(agent)
        testAgentRuntimes(agent)
        testOptions(agent)
        testJsonRecode(agent)
        testMarketplace(agent)
    }
})