package org.coralprotocol.coralserver.llm

data class LlmCallResult(
    val provider: String,
    val model: String?,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val durationMs: Long,
    val streaming: Boolean,
    val success: Boolean,
    val errorKind: String? = null,
    val statusCode: Int? = null,
    val chunkCount: Int? = null,
) {
    fun formatTokenInfo(): String {
        if (inputTokens == null && outputTokens == null) return ""
        return " tokens=${inputTokens ?: "?"}→${outputTokens ?: "?"}"
    }
}
