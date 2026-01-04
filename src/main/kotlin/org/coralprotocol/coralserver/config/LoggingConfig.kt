package org.coralprotocol.coralserver.config

import java.nio.file.Path

data class LoggingConfig(
    /**
     * The number of logging events to store in memory
     */
    val logBufferSize: UInt = 32u * 1024u,

    /**
     * Maximum number of logging events to replay to a new subscriber, regardless of what they requested
     */
    val maxReplay: UInt = 2048u,

    /**
     * https://logback.qos.ch/manual/appenders.html#tbrpFileNamePattern
     */
    val logFileNamePattern: String = Path.of(System.getProperty("user.home"), ".coral", "logs")
        .toString() + "/%d{yyyy/MM, aux}/%d{yyyy-MM-dd}.%i.log.gz",

    /**
     * https://logback.qos.ch/manual/appenders.html#tbrpMaxHistory
     */
    val maxHistory: Int = 12,

    /**
     * https://logback.qos.ch/manual/appenders.html#totalSizeCap
     */
    val logTotalSizeCap: String = "3GB",

    /**
     * https://logback.qos.ch/manual/appenders.html#tbrpCleanHistoryOnStart
     */
    val logClearHistoryOnStart: Boolean = false,

    /**
     * https://logback.qos.ch/manual/appenders.html#maxFileSize
     */
    val maxFileSize: String = "10MB"
)
