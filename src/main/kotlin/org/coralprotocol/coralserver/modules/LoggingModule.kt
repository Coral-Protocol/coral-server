package org.coralprotocol.coralserver.modules

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.NativeLoggingConditionalMdc
import org.coralprotocol.coralserver.logging.NativeLoggingConditionalMdcPlain
import org.coralprotocol.coralserver.logging.NativeLoggingMessageHighlighter
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets


const val LOGGER_ROUTES = "routeLogger"
const val LOGGER_CONFIG = "configLogger"
const val LOGGER_LOG_API = "apiLogger"
const val LOGGER_LOCAL_SESSION = "localSessionLogger"
const val LOGGER_TEST = "testLogger"

val loggingModule = module {
    single<org.slf4j.Logger> {
        val loggingConfig = get<LoggingConfig>()
        val logCtx = LoggerFactory.getILoggerFactory() as LoggerContext
        logCtx.reset()

        logCtx.getLogger("io.ktor.server.plugins.cors.CORS").level = Level.OFF

        // anthropic slop noise reduction
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.server.Server").level = Level.OFF
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.server.Protocol").level = Level.OFF
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.shared.Protocol").level = Level.OFF
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.server.FeatureNotificationService").level = Level.OFF
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.server.SessionNotificationJob").level = Level.OFF
        logCtx.getLogger("io.modelcontextprotocol.kotlin.sdk.server.ServerSessionRegistry").level = Level.OFF
        logCtx.getLogger("FeatureRegistry[Tool]").level = Level.OFF
        logCtx.getLogger("FeatureRegistry[Resource]").level = Level.OFF

        logCtx.putObject(
            CoreConstants.PATTERN_RULE_REGISTRY, mapOf(
                "msgHighlight" to NativeLoggingMessageHighlighter::class.java.name,
                "mdc" to NativeLoggingConditionalMdc::class.java.name,
                "mdcPlain" to NativeLoggingConditionalMdcPlain::class.java.name
            )
        )

        val consoleEncoder = PatternLayoutEncoder()
        consoleEncoder.setContext(logCtx)
        consoleEncoder.setPattern("%highlight(%5level) {%green(%d{yyyy-MM-dd HH:mm:ss.SSS})}%mdc{ns, sid, agent, io} %msgHighlight(%msg%n)")
        consoleEncoder.charset = StandardCharsets.UTF_8
        consoleEncoder.start()

        val consoleFilter = ThresholdFilter()
        consoleFilter.setLevel(loggingConfig.consoleLogLevel.toString())
        consoleFilter.start()

        val logConsoleAppender = ConsoleAppender<ILoggingEvent>()
        logConsoleAppender.setContext(logCtx)
        logConsoleAppender.name = "STDOUT"
        logConsoleAppender.isWithJansi = false
        logConsoleAppender.setEncoder(consoleEncoder)
        logConsoleAppender.addFilter(consoleFilter)
        logConsoleAppender.start()

        val fileEncoder = PatternLayoutEncoder()
        fileEncoder.setContext(logCtx)
        fileEncoder.setPattern("%level %d{yyyy-MM-dd HH:mm:ss.SSS} -%mdcPlain{ns, sid, agent, io} %msg%n")
        fileEncoder.start()

        val logFilePolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>()
        logFilePolicy.setContext(logCtx)
        logFilePolicy.setFileNamePattern(loggingConfig.logFileNamePattern)
        logFilePolicy.maxHistory = loggingConfig.maxHistory
        logFilePolicy.setTotalSizeCap(FileSize.valueOf(loggingConfig.logTotalSizeCap))
        logFilePolicy.setMaxFileSize(FileSize.valueOf(loggingConfig.maxFileSize))
        logFilePolicy.isCleanHistoryOnStart = loggingConfig.logClearHistoryOnStart

        val fileFilter = ThresholdFilter()
        fileFilter.setLevel(loggingConfig.fileLogLevel.toString())
        consoleFilter.start()

        val logFileAppender = RollingFileAppender<ILoggingEvent>()
        logFileAppender.setContext(logCtx)
        logFileAppender.setName("FILE")
        logFileAppender.isAppend = true
        logFileAppender.file = loggingConfig.logFileName
        logFileAppender.setEncoder(fileEncoder)
        logFileAppender.rollingPolicy = logFilePolicy
        logFileAppender.triggeringPolicy = logFilePolicy
        logFileAppender.addFilter(fileFilter)
        logFilePolicy.setParent(logFileAppender)
        logFilePolicy.start()
        logFileAppender.start()

        val log = logCtx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        log.isAdditive = false
        log.level = Level.TRACE
        log.addAppender(logConsoleAppender)
        if (loggingConfig.logToFileEnabled)
            log.addAppender(logFileAppender)
        log
    }

    single(createdAtStart = true) {
        val config by inject<LoggingConfig>()
        Logger(config.logBufferSize.toInt(), get())
    }
}

val namedLoggingModule = module {
    single<Logger>(named(LOGGER_ROUTES)) { get() }
    single<Logger>(named(LOGGER_CONFIG)) { get() }
    single<Logger>(named(LOGGER_LOG_API)) { get() }
    single<Logger>(named(LOGGER_LOCAL_SESSION)) { get() }
}