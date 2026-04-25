package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat

@Serializable
data class AgentLlmConfig(
    val proxies: List<AgentLlmProxyRequest> = emptyList()
)

@Serializable
data class AgentLlmProxyRequest(
    val name: String,
    val format: LlmProviderFormat,
    val models: Set<String> = setOf()
)
