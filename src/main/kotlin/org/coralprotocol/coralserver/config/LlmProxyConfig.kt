package org.coralprotocol.coralserver.config

import kotlinx.serialization.Serializable

@Serializable
data class LlmProxyConfig(
    val models: Map<String, LlmModelConfig> = emptyMap(),
    val providers: Map<String, LlmProviderConfig> = emptyMap(),
    val requestTimeoutSeconds: Long = 300
)

@Serializable
data class LlmModelConfig(
    val provider: String,
    val model: String
)

@Serializable
data class LlmProviderConfig(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val timeoutSeconds: Long? = null
)
