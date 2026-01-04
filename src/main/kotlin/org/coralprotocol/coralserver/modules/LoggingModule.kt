package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.koin.dsl.module

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
}