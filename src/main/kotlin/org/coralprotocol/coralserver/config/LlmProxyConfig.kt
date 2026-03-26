package org.coralprotocol.coralserver.config

data class LlmProxyConfig(
    val enabled: Boolean = false,
    val requestTimeoutSeconds: Long = 300,
    val providers: Map<String, LlmProxyProviderConfig> = emptyMap()
)

data class LlmProxyProviderConfig(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val timeoutSeconds: Long? = null
)
