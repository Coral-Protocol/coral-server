package org.coralprotocol.coralserver.config

/**
 * Off-host (microVM) agent hosting via coral-cloud. The MVP provider is a thin client that POSTs the
 * agent spec to cloud's `/provision` API; cloud owns the Fly fleet + the gateway the agent connects
 * back through.
 *
 * - [provisionUrl]: cloud's `/api/internal/coral/provision` URL. Null = sandbox runtime unavailable
 *   (the [org.coralprotocol.coralserver.agent.execution.ExecutionPolicyResolver] gate).
 * - [agentGatewayUrl]: cloud's public gateway base the agent phones home to (e.g.
 *   `https://cloud.example.com/sandbox`); becomes the agent's `CORAL_CONNECTION_URL` /
 *   `CORAL_PROXY_URL_*` base via [RootConfig.resolveBaseUrl] for [AddressConsumer.EXTERNAL].
 * - [apiKey]: Bearer token for `/provision`; falls back to [CloudConfig.apiKey] when null.
 */
data class SandboxConfig(
    val provisionUrl: String? = null,
    val agentGatewayUrl: String? = null,
    val apiKey: String? = null,
)
