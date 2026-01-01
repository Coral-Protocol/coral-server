package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.koin.dsl.module

val loggingModule = module {
    single<LoggingInterface> {
        val config by inject<LoggingConfig>()
        Logger(config.logBufferSize.toInt())
    }
}