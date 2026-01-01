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
        logger.info(message, *tags.toTypedArray())
    }

    override fun warn(message: () -> String) {
        logger.warn(message, *tags.toTypedArray())
    }

    override fun error(throwable: Throwable?, message: () -> String) {
        logger.error(throwable, message, *tags.toTypedArray())
    }

    override fun info(message: () -> String, vararg tags: LoggingTag) {
        logger.log(LoggingEvent.Info(message(), this.tags + tags))
    }

    override fun warn(message: () -> String, vararg tags: LoggingTag) {
        logger.log(LoggingEvent.Warning(message(), this.tags + tags))
    }

    override fun error(throwable: Throwable?, message: () -> String, vararg tags: LoggingTag) {
        logger.log(LoggingEvent.Error(message(), this.tags + tags, throwable))
    }
}