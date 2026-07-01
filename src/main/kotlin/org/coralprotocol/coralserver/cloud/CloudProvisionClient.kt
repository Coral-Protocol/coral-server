package org.coralprotocol.coralserver.cloud

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.SandboxConfig

/**
 * Thrown when cloud's `/provision` returns a non-2xx, so the failure surfaces as a clear HTTP error
 * (carrying cloud's message) instead of an opaque deserialization error on a non-[MachineHandle] body.
 */
class SandboxProvisionException(val status: HttpStatusCode, val responseBody: String) :
    RuntimeException("cloud /provision failed ($status): $responseBody")

/**
 * MVP [SandboxProvider]: a thin client that POSTs the agent spec to coral-cloud's `/provision` and
 * awaits the machine handle. No provider (Fly/…) SDK lives in coral-server.
 */
class CloudProvisionClient(
    private val httpClient: HttpClient,
    private val sandboxConfig: SandboxConfig,
    private val cloudConfig: CloudConfig,
) : SandboxProvider {
    override suspend fun provision(request: ProvisionRequest): MachineHandle {
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
