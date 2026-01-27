package org.coralprotocol.coralserver.logging

interface LoggingInterface {
    fun log(event: LoggingEvent)
    fun withTags(vararg tags: LoggingTag): LoggingInterface

    fun info(message: () -> String)
    fun warn(message: () -> String)
    fun debug(message: () -> String)
    fun trace(message: () -> String)
    fun error(throwable: Throwable? = null, message: () -> String)

    fun info(vararg tags: LoggingTag, message: () -> String)
    fun warn(vararg tags: LoggingTag, message: () -> String)
    fun debug(vararg tags: LoggingTag, message: () -> String)
    fun trace(vararg tags: LoggingTag, message: () -> String)
    fun error(vararg tags: LoggingTag, throwable: Throwable?, message: () -> String)
}