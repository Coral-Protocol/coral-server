package org.coralprotocol.coralserver.routes.ui

import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.logging.Logger
import org.koin.ktor.ext.inject
import java.io.File

fun Route.consoleUi() {
    val logger by inject<Logger>()
    val path = System.getenv("CONSOLE_UI_PATH")

    if (path == null) {
        logger.warn { "CONSOLE_UI_PATH is not set, Console UI will not be available" }
    } else {
        val file = File(path)
        if (!file.exists()) {
            logger.warn { "CONSOLE_UI_PATH is set to ${file.absolutePath} which does not exist" }
        } else {
            singlePageApplication {
//                applicationRoute = "console"
                filesPath = file.absolutePath
            }
        }
    }
}