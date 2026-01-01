package org.coralprotocol.coralserver.logging

interface LoggingInterface {
    fun info(message: () -> String)
    fun warn(message: () -> String)
    fun error(throwable: Throwable? = null, message: () -> String)
    fun info(message: () -> String, tags: Set<LoggingTag> = setOf())
    fun warn(message: () -> String, tags: Set<LoggingTag> = setOf())
    fun error(throwable: Throwable?, message: () -> String, tags: Set<LoggingTag> = setOf())
}