package org.coralprotocol.coralserver.cloud

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.SandboxConfig

class CloudProvisionClientTest : FunSpec({

    val request = ProvisionRequest(
        agentName = "researcher",
        coralSession = "sess-1",
        image = "img@sha256:abc",
        env = mapOf("CORAL_AGENT_SECRET" to "SEKRET"),
        egress = Egress(declared = listOf(Endpoint("api.firecrawl.dev"))),
        resources = Resources(cpus = 1, memoryMb = 512),
    )

    fun client(
        sandboxConfig: SandboxConfig,
        cloudConfig: CloudConfig = CloudConfig(),
        handler: MockRequestHandler,
    ): CloudProvisionClient {
        val http = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(Json { encodeDefaults = true; explicitNulls = false }) }
            engine { addHandler(handler) }
        }
        return CloudProvisionClient(http, sandboxConfig, cloudConfig)
    }

    fun MockRequestHandleScope.handle() = respond(
        content = """{"machine_id":"m-123"}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    test("POSTs the snake_case spec to provision_url with bearer auth and returns the handle") {
        lateinit var captured: HttpRequestData
        val provision = client(
            SandboxConfig(provisionUrl = "https://cloud.example.com/provision", apiKey = "sandbox-key"),
        ) { req -> captured = req; handle() }

        provision.provision(request).machineId shouldBe "m-123"

        captured.method shouldBe HttpMethod.Post
        captured.url.toString() shouldBe "https://cloud.example.com/provision"
        captured.headers[HttpHeaders.Authorization] shouldBe "Bearer sandbox-key"
        (captured.body as TextContent).text.let { body ->
            body shouldContain "\"agent_name\":\"researcher\""
            body shouldContain "\"memory_mb\":512"
        }
    }

    test("apiKey falls back to cloud config when the sandbox key is null") {
        lateinit var captured: HttpRequestData
        val provision = client(
            SandboxConfig(provisionUrl = "https://cloud.example.com/provision"),
            CloudConfig(apiKey = "cloud-key"),
        ) { req -> captured = req; handle() }

        provision.provision(request)

        captured.headers[HttpHeaders.Authorization] shouldBe "Bearer cloud-key"
    }

    test("non-2xx maps to SandboxProvisionException carrying the status and body") {
        val provision = client(
            SandboxConfig(provisionUrl = "https://cloud.example.com/provision", apiKey = "k"),
        ) { respond("boom", HttpStatusCode.BadGateway) }

        val ex = shouldThrow<SandboxProvisionException> { provision.provision(request) }
        ex.status shouldBe HttpStatusCode.BadGateway
        ex.responseBody shouldBe "boom"
    }
})
