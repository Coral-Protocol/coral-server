package org.coralprotocol.coralserver.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provider-agnostic SPI for off-host agent sandboxes. coral-server owns the per-agent spec; the
 * actual provider adapter (Fly/Northflank/Azure) lives in coral-cloud behind `/provision`. The MVP
 * implementation is [CloudProvisionClient].
 */
interface SandboxProvider {
    suspend fun provision(request: ProvisionRequest): MachineHandle
}

/** Wire shape of cloud's `POST /api/internal/coral/provision` body (snake_case to match cloud's serde). */
@Serializable
data class ProvisionRequest(
    @SerialName("agent_name") val agentName: String,
    @SerialName("coral_session") val coralSession: String,
    // Forward-compat: cloud uses its controlled base image for the MVP and ignores this.
    val image: String? = null,
    // Opaque env built by coral-server (CORAL_CONNECTION_URL/CORAL_AGENT_SECRET/CORAL_PROXY_URL_*…).
    val env: Map<String, String>,
    val egress: Egress,
    val resources: Resources? = null,
)

/** Author-declared external hosts; cloud injects its own gateway endpoint, so only `declared` is sent. */
@Serializable
data class Egress(val declared: List<Endpoint>)

@Serializable
data class Endpoint(val host: String, val port: Int)

@Serializable
data class Resources(val cpus: Int, @SerialName("memory_mb") val memoryMb: Int)

@Serializable
data class MachineHandle(
    @SerialName("machine_id") val machineId: String,
    @SerialName("private_ip") val privateIp: String? = null,
)
