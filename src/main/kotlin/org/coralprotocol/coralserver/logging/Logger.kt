@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.component.KoinComponent
import org.slf4j.MDC

class Logger(
    bufferSize: Int = 1024,
    val nativeLogger: org.slf4j.Logger
) : KoinComponent, LoggingInterface {
    val flow = MutableSharedFlow<LoggingEvent>(
        replay = bufferSize,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun log(event: LoggingEvent) {
        flow.tryEmit(event)

        val mdcKeys = event.tags
            .flatMap { it.mdcMap.entries }
            .associate { it.toPair() }

        mdcKeys.forEach { MDC.put(it.key, it.value) }
        event.log(nativeLogger)
        mdcKeys.forEach { MDC.remove(it.key) }
    }

    override fun withTags(vararg tags: LoggingTag) = LoggerWithTags(this, tags.toSet())

    override fun info(message: () -> String) {
        log(LoggingEvent.Info(message()))
    }

    override fun warn(message: () -> String) {
        log(LoggingEvent.Warning(message()))
    }

    override fun error(throwable: Throwable?, message: () -> String) {
        log(LoggingEvent.Error(error = throwable, text = message()))
    }

    override fun info(vararg tags: LoggingTag, message: () -> String) {
        log(LoggingEvent.Info(message(), tags.toSet()))
    }

    override fun warn(vararg tags: LoggingTag, message: () -> String) {
        log(LoggingEvent.Warning(message(), tags.toSet()))
    }

    override fun error(vararg tags: LoggingTag, throwable: Throwable?, message: () -> String) {
        log(LoggingEvent.Error(message(), tags.toSet(), throwable))
    }
}

