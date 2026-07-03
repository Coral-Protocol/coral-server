package org.coralprotocol.coralserver.cloud

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.SandboxConfig

/** Wire shape of cloud's `POST /api/internal/coral/provision` body (snake_case to match cloud's serde). */
@Serializable
data class ProvisionRequest(
    @SerialName("agent_name") val agentName: String,
    @SerialName("coral_session") val coralSession: String,
    // The author's OCI image (digest-pinned by the trust profile). Runs as the non-root agent
    // container, wrapped by cloud's egress sidecar in the same microVM.
    val image: String,
    // Opaque env built by coral-server (CORAL_CONNECTION_URL/CORAL_AGENT_SECRET/CORAL_PROXY_URL_*…).
    val env: Map<String, String>,
    val egress: Egress,
    val resources: Resources? = null,
)

/** Author-declared external hosts; cloud injects its own gateway endpoint, so only `declared` is sent. */
@Serializable
data class Egress(val declared: List<Endpoint>)

/** Cloud enforces egress at domain granularity, so only the host is carried (no port). */
@Serializable
data class Endpoint(val host: String)

@Serializable
data class Resources(val cpus: Int, @SerialName("memory_mb") val memoryMb: Int)

@Serializable
data class MachineHandle(@SerialName("machine_id") val machineId: String)

/**
 * Thrown when cloud's `/provision` returns a non-2xx, so the failure surfaces as a clear HTTP error
 * (carrying cloud's message) instead of an opaque deserialization error on a non-[MachineHandle] body.
 */
class SandboxProvisionException(val status: HttpStatusCode, val responseBody: String) :
    RuntimeException("cloud /provision failed ($status): $responseBody")

/**
 * Off-host agent provisioning: POSTs the per-agent spec to coral-cloud's `/provision` and awaits the
 * machine handle. coral-server holds no provider (Fly/…) SDK; cloud owns the fleet and the gateway.
 */
class CloudProvisionClient(
    private val httpClient: HttpClient,
    private val sandboxConfig: SandboxConfig,
    private val cloudConfig: CloudConfig,
) {
    suspend fun provision(request: ProvisionRequest): MachineHandle {
        val url = sandboxConfig.provisionUrl
            ?: error("sandbox.provision_url is not configured (should have been caught by ExecutionPolicyResolver)")
        val apiKey = sandboxConfig.apiKey ?: cloudConfig.apiKey

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            if (apiKey != null) bearerAuth(apiKey)
            setBody(request)
        }
        if (!response.status.isSuccess())
            throw SandboxProvisionException(response.status, response.bodyAsText())

        return response.body()
    }
}
