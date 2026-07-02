package org.coralprotocol.coralserver.agent.runtime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
import org.coralprotocol.coralserver.cloud.Egress
import org.coralprotocol.coralserver.cloud.Endpoint
import org.coralprotocol.coralserver.cloud.ProvisionRequest
import org.coralprotocol.coralserver.cloud.Resources
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.RootConfig
import org.coralprotocol.coralserver.config.SandboxConfig
import org.coralprotocol.coralserver.routes.mcp.v1.Sse

class SandboxRuntimeTest : FunSpec({

    test("resolveBaseUrl EXTERNAL returns the configured cloud gateway base") {
        val config = RootConfig(
            sandboxConfig = SandboxConfig(agentGatewayUrl = "https://cloud.example.com/sandbox"),
        )
        config.resolveBaseUrl(AddressConsumer.EXTERNAL).toString() shouldBe "https://cloud.example.com/sandbox"
    }

    test("resolveBaseUrl non-EXTERNAL still uses the local bind URL") {
        RootConfig().resolveBaseUrl(AddressConsumer.LOCAL).toString() shouldStartWith "http://localhost"
    }

    // The load-bearing check: building the MCP URL off the gateway base must keep the /sandbox prefix
    // AND the secret in the path. `href` overwrites the path, so the getters call prependBasePath to
    // restore the gateway prefix; this exercises that exact path.
    test("external MCP URL keeps the /sandbox prefix with the secret in the path") {
        val base = Url("https://cloud.example.com/sandbox")
        val builder = URLBuilder(base)
        href(ResourcesFormat(), Sse(agentSecret = "SEKRET"), builder)
        builder.prependBasePath(base).build().toString() shouldBe
            "https://cloud.example.com/sandbox/mcp/v1/SEKRET/sse/"
    }

    test("ProvisionRequest serializes to cloud's snake_case wire shape") {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val request = ProvisionRequest(
            agentName = "researcher",
            coralSession = "sess-1",
            image = "registry.fly.io/authors/researcher@sha256:abc",
            env = mapOf("CORAL_AGENT_SECRET" to "SEKRET"),
            egress = Egress(declared = listOf(Endpoint("api.firecrawl.dev", 443))),
            resources = Resources(cpus = 1, memoryMb = 512),
        )

        val obj = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        obj["agent_name"]!!.jsonPrimitive.content shouldBe "researcher"
        obj["coral_session"]!!.jsonPrimitive.content shouldBe "sess-1"
        obj["image"]!!.jsonPrimitive.content shouldBe "registry.fly.io/authors/researcher@sha256:abc"
        obj["egress"]!!.jsonObject["declared"]!!.jsonArray[0]
            .jsonObject["host"]!!.jsonPrimitive.content shouldBe "api.firecrawl.dev"
        obj["resources"]!!.jsonObject["memory_mb"]!!.jsonPrimitive.int shouldBe 512
    }

    test("sandboxResources maps docker limits to Fly guest sizing, flooring sub-1 vCPU") {
        sandboxResources(
            DockerExecutionTrustPolicy(nanoCpus = 2_000_000_000L, memoryLimitBytes = 1024L * 1024 * 1024)
        ) shouldBe Resources(cpus = 2, memoryMb = 1024)

        sandboxResources(DockerExecutionTrustPolicy(nanoCpus = 500_000_000L)) shouldBe
            Resources(cpus = 1, memoryMb = 512)
    }

    test("sandboxResources is null when the trust profile sets no limits") {
        sandboxResources(DockerExecutionTrustPolicy()) shouldBe null
    }
})
