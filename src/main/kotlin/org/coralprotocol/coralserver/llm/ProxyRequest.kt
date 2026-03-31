package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.session.SessionAgent

data class ProxyRequest(
    val agent: SessionAgent,
    val profile: LlmProviderProfile,
    val apiKey: String,
    val upstreamUrl: String,
    val timeoutMs: Long,
    val requestBody: String,
    val model: String?,
    val hasBody: Boolean,
    val startTime: Long
)
