package org.coralprotocol.coralserver.routes.ui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.koin.core.component.inject
import org.koin.ktor.ext.inject
import java.io.File
import kotlin.getValue

fun Route.consoleUi() {
    val logger by inject<LoggingInterface>()
    val path = System.getenv("CONSOLE_UI_PATH")

    if (path == null) {
        logger.warn { "CONSOLE_UI_PATH is not set, Console UI will not be available" }
    }
    else {
        val file = File(path)
        if (!file.exists()) {
            logger.warn { "CONSOLE_UI_PATH is set to ${file.absolutePath} which does not exist" }
        }
        else {
            singlePageApplication {
//                applicationRoute = "console"
                filesPath = file.absolutePath
            }
        }
    }
}