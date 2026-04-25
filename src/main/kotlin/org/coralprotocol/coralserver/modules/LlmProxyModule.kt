package org.coralprotocol.coralserver.modules

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
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
                        response.status == HttpStatusCode.Conflict || response.status.value in 500..599
                    }
                    exponentialDelay(
                        base = 2.0,
                        baseDelayMs = config.retryInitialDelay.inWholeMilliseconds,
                        maxDelayMs = config.retryMaxDelay.inWholeMilliseconds
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
