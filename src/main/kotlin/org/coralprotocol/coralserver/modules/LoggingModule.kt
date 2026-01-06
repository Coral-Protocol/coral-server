package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val LOGGER_ROUTES = "routeLogger"
const val LOGGER_CONFIG = "configLogger"
const val LOGGER_LOG_API = "apiLogger"
const val LOGGER_LOCAL_SESSION = "localSessionLogger"
const val LOGGER_TEST = "testLogger"

val loggingModule = module {
    single(createdAtStart = true) {

        val config by inject<LoggingConfig>()

        System.setProperty("FILE_NAME_PATTERN", config.logFileNamePattern)
        System.setProperty("MAX_HISTORY", config.maxHistory.toString())
        System.setProperty("TOTAL_SIZE_CAP", config.logTotalSizeCap)
        System.setProperty("CLEAR_HISTORY_ON_START", config.logClearHistoryOnStart.toString())
        System.setProperty("MAX_FILE_SIZE", config.maxFileSize)

        Logger(config.logBufferSize.toInt())
    }

    single<Logger>(named(LOGGER_ROUTES)) { get() }
    single<Logger>(named(LOGGER_CONFIG)) { get() }
    single<Logger>(named(LOGGER_LOG_API)) { get() }
}