package org.coralprotocol.coralserver.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import org.fusesource.jansi.Ansi.ansi
import org.slf4j.MDC

class NativeLoggingConditionalMdc : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String {
        val data = optionList.toList()
            .associateWith { (event.mdcPropertyMap[it] ?: MDC.get(it)) }
            .mapNotNull { (key, value) ->
                value?.let { key.toString() to value }
            }
            .joinToString(", ") {
                ansi().fgBrightMagenta().a(it.first).reset().a("=").fgBrightBlue().a(it.second).reset().toString()
            }

        return if (data.isEmpty()) {
            ""
        } else {
            " [$data]"
        }
    }
}