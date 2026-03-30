package org.coralprotocol.coralserver.llm

enum class LlmProviderProfile(
    val providerId: String,
    val defaultBaseUrl: String,
    val authStyle: AuthStyle,
    val defaultHeaders: Map<String, String>,
    val sdkBaseUrlEnvVar: String? = null,
    val sdkPathSuffix: String = ""
) {
    OPENAI("openai", "https://api.openai.com", AuthStyle.Bearer, emptyMap(),
        sdkBaseUrlEnvVar = "OPENAI_BASE_URL", sdkPathSuffix = "/v1"),
    ANTHROPIC("anthropic", "https://api.anthropic.com", AuthStyle.Custom("x-api-key"), mapOf("anthropic-version" to "2023-06-01"),
        sdkBaseUrlEnvVar = "ANTHROPIC_BASE_URL"),
    OPENROUTER("openrouter", "https://openrouter.ai", AuthStyle.Bearer, emptyMap(),
        sdkBaseUrlEnvVar = "OPENROUTER_BASE_URL"),
    MOCK("mock", "http://mock", AuthStyle.Bearer, emptyMap());

    companion object {
        private val byId = entries.associateBy { it.providerId }

        fun fromId(id: String): LlmProviderProfile? = byId[id.lowercase()]
    }
}

sealed class AuthStyle {
    data object Bearer : AuthStyle()
    data class Custom(val headerName: String) : AuthStyle()
}
