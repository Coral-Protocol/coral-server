package org.coralprotocol.coralserver.config

import kotlinx.serialization.Serializable

@Serializable
data class LlmProxyConfig(
    val engines: Map<String, LlmEngineConfig> = emptyMap(),
    val providers: Map<String, LlmProviderConfig> = emptyMap()
)

@Serializable
data class LlmEngineConfig(
    val provider: String,
    val engine: String
)

@Serializable
data class LlmProviderConfig(
    val baseUrl: String? = null,
    val apiKey: String? = null
)
