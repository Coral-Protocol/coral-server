package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.llm.LlmProxyService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val llmModule = module {
    single { LlmProxyService(get(), get()) }
}
