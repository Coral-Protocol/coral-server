package org.coralprotocol.coralserver.cloud

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.SandboxConfig

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

        return httpClient.post(url) {
            contentType(ContentType.Application.Json)
            if (apiKey != null) bearerAuth(apiKey)
            setBody(request)
        }.body()
    }
}
