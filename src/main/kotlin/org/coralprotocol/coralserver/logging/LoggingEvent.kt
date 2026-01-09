@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package org.coralprotocol.coralserver.logging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
@JsonClassDiscriminator("type")
sealed class LoggingEvent {
    abstract fun log(nativeLogger: org.slf4j.Logger)
    abstract val tags: Set<LoggingTag>

    @Suppress("unused")
    val timestampUtc = Clock.System.now().toString()

    @Serializable
    @SerialName("info")
    data class Info(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),
    ) : LoggingEvent() {
        override fun log(nativeLogger: org.slf4j.Logger) {
            nativeLogger.info(text)
        }
    }

    @Serializable
    @SerialName("warning")
    data class Warning(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),
    ) : LoggingEvent() {
        override fun log(nativeLogger: org.slf4j.Logger) {
            nativeLogger.warn(text)
        }
    }

    @Serializable
    @SerialName("error")
    data class Error(
        val text: String,
        override val tags: Set<LoggingTag> = setOf(),

        @Transient
        val error: Throwable? = null,
    ) : LoggingEvent() {
        @Suppress("unused")
        val exceptionStackTrace = error?.stackTrace?.map { it.toString() } ?: emptyList()

        override fun log(nativeLogger: org.slf4j.Logger) {
            nativeLogger.error(text, error)
        }
    }
}