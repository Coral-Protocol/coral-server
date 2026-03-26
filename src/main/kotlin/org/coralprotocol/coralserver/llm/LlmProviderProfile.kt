package org.coralprotocol.coralserver.llm

enum class LlmProviderProfile(
    val providerId: String,
    val defaultBaseUrl: String,
    val authStyle: AuthStyle,
    val defaultHeaders: Map<String, String>
) {
    OPENAI("openai", "https://api.openai.com", AuthStyle.Bearer, emptyMap()),
    ANTHROPIC("anthropic", "https://api.anthropic.com", AuthStyle.Custom("x-api-key"), mapOf("anthropic-version" to "2023-06-01")),
    OPENROUTER("openrouter", "https://openrouter.ai/api", AuthStyle.Bearer, emptyMap()),
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
