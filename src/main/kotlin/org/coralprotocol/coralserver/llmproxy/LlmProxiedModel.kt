package org.coralprotocol.coralserver.llmproxy

import org.coralprotocol.coralserver.config.LlmProxyProviderConfig

data class LlmProxiedModel(
    val providerConfig: LlmProxyProviderConfig,
    val modelName: String,
)
