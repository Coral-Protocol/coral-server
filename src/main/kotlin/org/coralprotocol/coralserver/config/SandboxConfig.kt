package org.coralprotocol.coralserver.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Off-host (microVM) agent hosting via coral-cloud; a null [provisionUrl] disables the sandbox runtime. */
data class SandboxConfig(
    val provisionUrl: String? = null,
    val agentGatewayUrl: String? = null,
    val apiKey: String? = null, // falls back to CloudConfig.apiKey
    // Generous: Fly cold-start + author image pull can be slow.
    val connectTimeout: Duration = 5.minutes,
)
