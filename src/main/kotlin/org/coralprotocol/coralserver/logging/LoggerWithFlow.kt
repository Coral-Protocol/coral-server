@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("level")
sealed interface LogMessage {
    @Serializable
    @SerialName("info")
    data class Info(val message: String) : LogMessage

    @Serializable
    @SerialName("warn")
    data class Warn(val message: String) : LogMessage

    @Serializable
    @SerialName("error")
    data class Error(val message: String, val stackTrace: List<String>) : LogMessage
}

class LoggerWithFlow(name: String) {
    private val logger = KotlinLogging.logger(name)
    private val messageFlow = MutableSharedFlow<LogMessage>(replay = 1024)

    fun info(message: String) {
        logger.info { message }
        messageFlow.tryEmit(LogMessage.Info(message))
    }

    fun warn(message: String) {
        logger.warn { message }
        messageFlow.tryEmit(LogMessage.Warn(message))
    }

    fun error(message: String, exception: Exception) {
        logger.error(exception) { message }
        messageFlow.tryEmit(LogMessage.Error(message, exception.stackTrace.map { it.toString() }))
    }

    fun getSharedFlow() = messageFlow.asSharedFlow()
}