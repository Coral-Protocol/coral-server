package org.coralprotocol.coralserver.llmproxy

enum class LlmProviderProfile(
    val providerId: String,
    val defaultBaseUrl: String,
    val authStyle: AuthStyle,
    val defaultHeaders: Map<String, String>,
    val strategy: LlmProviderStrategy,
    val sdkBaseUrlEnvVar: String? = null,
    val sdkPathSuffix: String = ""
) {
    OPENAI(
        "openai", "https://api.openai.com", AuthStyle.Bearer, emptyMap(), OpenAIStrategy,
        sdkBaseUrlEnvVar = "OPENAI_BASE_URL", sdkPathSuffix = "v1"
    ),

    ANTHROPIC(
        "anthropic",
        "https://api.anthropic.com",
        AuthStyle.Custom("x-api-key"),
        mapOf("anthropic-version" to "2023-06-01"),
        AnthropicStrategy,
        sdkBaseUrlEnvVar = "ANTHROPIC_BASE_URL"
    ),

    OPENROUTER(
        "openrouter", "https://openrouter.ai", AuthStyle.Bearer, emptyMap(), OpenAIStrategy,
        sdkBaseUrlEnvVar = "OPENROUTER_BASE_URL"
    );

    companion object {
        private val byId = entries.associateBy { it.providerId }

        fun fromId(id: String): LlmProviderProfile? = byId[id.lowercase()]
    }
}

sealed class AuthStyle {
    data object Bearer : AuthStyle()
    data class Custom(val headerName: String) : AuthStyle()
}
