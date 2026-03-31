package org.coralprotocol.coralserver.config

data class LlmProxyConfig(
    val enabled: Boolean = true,
    val requestTimeoutSeconds: Long = 300,
    val retryMaxAttempts: Int = 0,
    val retryInitialDelayMs: Long = 1000,
    val retryMaxDelayMs: Long = 10000,
    val providers: Map<String, LlmProxyProviderConfig> = emptyMap()
)

data class LlmProxyProviderConfig(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val timeoutSeconds: Long? = null
)
