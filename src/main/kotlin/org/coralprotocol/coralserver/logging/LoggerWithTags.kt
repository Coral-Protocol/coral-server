package org.coralprotocol.coralserver.logging

class LoggerWithTags(
    val logger: Logger,
    val tags: Set<LoggingTag> = setOf(),
) : LoggingInterface {
    override fun info(message: () -> String) {
        logger.info(message, tags)
    }

    override fun warn(message: () -> String) {
        logger.warn(message, tags)
    }

    override fun error(throwable: Throwable?, message: () -> String) {
        logger.error(throwable, message, tags)
    }

    override fun info(message: () -> String, tags: Set<LoggingTag>) {
        logger.log(LoggingEvent.Info(message(), this.tags + tags))
    }

    override fun warn(message: () -> String, tags: Set<LoggingTag>) {
        logger.log(LoggingEvent.Warning(message(), this.tags + tags))
    }

    override fun error(throwable: Throwable?, message: () -> String, tags: Set<LoggingTag>) {
        logger.log(LoggingEvent.Error(message(), this.tags + tags, throwable))
    }
}