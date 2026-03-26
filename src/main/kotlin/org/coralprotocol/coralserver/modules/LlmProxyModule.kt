package org.coralprotocol.coralserver.modules

import io.ktor.client.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.llm.LlmProxyService
import org.coralprotocol.coralserver.logging.Logger
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val LOGGER_LLM_PROXY = "llm-proxy"

val llmProxyModule = module {
    single {
        LlmProxyService(
            config = get(),
            httpClient = get(),
            json = get(),
            logger = get(named(LOGGER_LLM_PROXY))
        )
    }
}
