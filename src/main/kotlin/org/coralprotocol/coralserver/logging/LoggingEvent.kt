@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.logging

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface LoggingEvent {
    fun log(nativeLogger: KLogger)
    val tags: Set<LoggingTag>

    @Serializable
    @SerialName("info")
    data class Info(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),
    ) : LoggingEvent {
        override fun log(nativeLogger: KLogger) {
            nativeLogger.info { text }
        }
    }

    @Serializable
    @SerialName("warning")
    data class Warning(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),
    ) : LoggingEvent {
        override fun log(nativeLogger: KLogger) {
            nativeLogger.warn { text }
        }
    }

    @Serializable
    @SerialName("error")
    data class Error(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),

        @Transient
        val error: Throwable? = null,
    ) : LoggingEvent {
        @Suppress("unused")
        val exceptionStackTrace = error?.stackTrace?.map { it.toString() } ?: emptyList()

        override fun log(nativeLogger: KLogger) {
            nativeLogger.error(error) { text }
        }
    }
}