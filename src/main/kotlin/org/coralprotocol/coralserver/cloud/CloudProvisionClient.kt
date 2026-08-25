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
    val image: String,
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

/** A non-2xx from `/provision`, surfaced with cloud's status + body. */
class SandboxProvisionException(val status: HttpStatusCode, val responseBody: String) :
    RuntimeException("cloud /provision failed ($status): $responseBody")

/** POSTs the per-agent spec to coral-cloud's `/provision`; no provider (Fly/…) SDK lives here. */
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
