package org.coralprotocol.coralserver.routes.ui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import java.io.File

private val logger = KotlinLogging.logger {}

fun Route.consoleUi() {
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
            staticFiles("console", file)
        }
    }
}