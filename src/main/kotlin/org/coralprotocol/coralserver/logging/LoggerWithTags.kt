package org.coralprotocol.coralserver.logging

class LoggerWithTags(
    val logger: LoggingInterface,
    val tags: Set<LoggingTag> = setOf(),
) : LoggingInterface {
    override fun log(event: LoggingEvent) {
        logger.log(event)
    }

    override fun withTags(vararg tags: LoggingTag): LoggingInterface =
        logger.withTags(*(this.tags + tags.toSet()).toTypedArray())

    override fun info(message: () -> String) {
        logger.info(
            tags = tags.toTypedArray(),
            message = message,
        )
    }

    override fun warn(message: () -> String) {
        logger.warn(
            tags = tags.toTypedArray(),
            message = message,
        )
    }

    override fun error(throwable: Throwable?, message: () -> String) {
        logger.error(
            tags = tags.toTypedArray(),
            throwable = throwable,
            message = message,
        )
    }

    override fun info(vararg tags: LoggingTag, message: () -> String) {
        logger.log(LoggingEvent.Info(message(), this.tags + tags))
    }

    override fun warn(vararg tags: LoggingTag, message: () -> String) {
        logger.log(LoggingEvent.Warning(message(), this.tags + tags))
    }

    override fun error(vararg tags: LoggingTag, throwable: Throwable?, message: () -> String) {
        logger.log(LoggingEvent.Error(message(), this.tags + tags, throwable))
    }
}