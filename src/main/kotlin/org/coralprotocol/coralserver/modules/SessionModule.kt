package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sessionModule = module {
    singleOf(::ApplicationRuntimeContext)
    single {
        LocalSessionManager(
            blockchainService = get(),
            jupiterService = get(),
            httpClient = get(),
            config = get()
        )
    }
}