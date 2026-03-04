package org.coralprotocol.coralserver.config

import org.slf4j.event.Level
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
     * If this is false, log messages will not be written to file regardless of level or other configuration
     */
    val logToFileEnabled: Boolean = true,

    /**
     * Root directory for all log files
     */
    val logFilesDirectory: String = Path.of(System.getProperty("user.home"), ".coral", "logs").toString(),

    /**
     * https://logback.qos.ch/manual/appenders.html#file
     */
    val logFileName: String = Path.of(logFilesDirectory, "server.log").toString(),

    /**
     * https://logback.qos.ch/manual/appenders.html#tbrpFileNamePattern
     */
    val logFileNamePattern: String = Path.of(
        logFilesDirectory,
        "archive",
        "%d{yyyy/MM, aux}",
        "%d{yyyy-MM-dd}.%i.log.gz"
    ).toString(),

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
    val maxFileSize: String = "10MB",

    /**
     * Maximum logging level to print to the console
     */
    val consoleLogLevel: Level = Level.INFO,

    /**
     * Maximum logging level to write to file
     */
    val fileLogLevel: Level = Level.INFO
)
