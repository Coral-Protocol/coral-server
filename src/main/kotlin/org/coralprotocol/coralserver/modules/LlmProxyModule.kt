package org.coralprotocol.coralserver.modules

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.llm.LlmProxyService
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val LOGGER_LLM_PROXY = "llm-proxy"
const val LLM_PROXY_HTTP_CLIENT = "llmProxyHttpClient"

val llmProxyModule = module {
    single(named(LLM_PROXY_HTTP_CLIENT)) {
        val config = get<LlmProxyConfig>()
        HttpClient(CIO) {
            if (config.retryMaxAttempts > 0) {
                install(HttpRequestRetry) {
                    maxRetries = config.retryMaxAttempts
                    retryIf { _, response ->
                        response.status.value == 429 || response.status.value in 500..599
                    }
                    exponentialDelay(
                        base = config.retryInitialDelayMs.toDouble() / 1000.0,
                        maxDelayMs = config.retryMaxDelayMs
                    )
                }
            }
        }
    }
    single {
        LlmProxyService(
            config = get(),
            httpClient = get(named(LLM_PROXY_HTTP_CLIENT)),
            json = get()
        )
    }
}
