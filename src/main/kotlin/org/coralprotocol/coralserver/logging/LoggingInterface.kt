package org.coralprotocol.coralserver.logging

interface LoggingInterface {
    fun log(event: LoggingEvent)
    fun withTags(vararg tags: LoggingTag): LoggingInterface
    fun info(message: () -> String)
    fun warn(message: () -> String)
    fun error(throwable: Throwable? = null, message: () -> String)
    fun info(message: () -> String, vararg tags: LoggingTag)
    fun warn(message: () -> String, vararg tags: LoggingTag)
    fun error(throwable: Throwable?, message: () -> String, vararg tags: LoggingTag)
}