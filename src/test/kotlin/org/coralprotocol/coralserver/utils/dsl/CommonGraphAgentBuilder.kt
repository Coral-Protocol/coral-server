package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.dsl.CommonGraphAgentBuilder
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.coralprotocol.coralserver.utils.TestProxy
import kotlin.collections.set

fun CommonGraphAgentBuilder.testProxy(testProxy: TestProxy, modelName: String) {
    proxies[testProxy.providerConfig.name] = LlmProxiedModel(testProxy.providerConfig, modelName)
}