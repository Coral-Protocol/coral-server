package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.registry.AgentLlmProxyRequest
import org.coralprotocol.coralserver.dsl.AgentLlmConfigBuilder
import org.coralprotocol.coralserver.utils.TestProxy

fun AgentLlmConfigBuilder.testProxy(testProxy: TestProxy) {
    proxies += AgentLlmProxyRequest(
        testProxy.providerConfig.name,
        testProxy.providerConfig.format,
        testProxy.providerConfig.models
    )
}