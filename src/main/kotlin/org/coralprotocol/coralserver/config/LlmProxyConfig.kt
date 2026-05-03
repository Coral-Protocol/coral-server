package org.coralprotocol.coralserver.config

import me.saket.bytesize.BinaryByteSize
import me.saket.bytesize.mebibytes
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class LlmProxyConfig(
    val retryMaxAttempts: Int = 0,
    val retryInitialDelay: Duration = 1.seconds,
    val retryMaxDelay: Duration = 10.seconds,
    val maxRequestSize: BinaryByteSize = 20.mebibytes,
    val maxResponseSize: BinaryByteSize = 80.mebibytes,
    val maxStreamChars: BinaryByteSize = 80.mebibytes,
    val providers: List<LlmProxyProviderConfig> = listOf()
)

data class LlmProxyProviderConfig(
    val name: String,
    val format: LlmProviderFormat,
    val models: Set<String> = setOf(),
    val apiKey: String,
    val baseUrl: String,
    val timeout: Duration = 10.minutes,
    val allowAnyModel: Boolean = models.isEmpty()
)
