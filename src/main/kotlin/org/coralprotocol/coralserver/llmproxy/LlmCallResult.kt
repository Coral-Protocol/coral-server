package org.coralprotocol.coralserver.llmproxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LlmErrorKind {
    @SerialName("rate_limited") RATE_LIMITED,
    @SerialName("credentials") CREDENTIALS,
    @SerialName("upstream_health") UPSTREAM_HEALTH,
    @SerialName("request_error") REQUEST_ERROR,
    @SerialName("connectivity") CONNECTIVITY,
    @SerialName("response_too_large") RESPONSE_TOO_LARGE,
    @SerialName("unknown") UNKNOWN
}

data class LlmCallResult(
    val provider: String,
    val model: String?,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val durationMs: Long,
    val streaming: Boolean,
    val success: Boolean,
    val errorKind: LlmErrorKind? = null,
    val statusCode: Int? = null,
    val chunkCount: Int? = null,
) {
    fun formatTokenInfo(): String {
        if (inputTokens == null && outputTokens == null) return ""
        return " tokens=${inputTokens ?: "?"}→${outputTokens ?: "?"}"
    }
}
