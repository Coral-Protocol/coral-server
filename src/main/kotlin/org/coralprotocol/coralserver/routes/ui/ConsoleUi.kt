package org.coralprotocol.coralserver.routes.ui

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.ConsoleConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.time.measureTime

fun Route.consoleUi() {
    val logger by inject<Logger>(named(LOGGER_ROUTES))
    val config by inject<ConsoleConfig>()

    val bundlePath = try {
        val cachePath = Path.of(config.cachePath)
        if (!cachePath.exists())
            cachePath.createDirectory()

        val bundlePath = cachePath.resolve(config.consoleReleaseVersion)

        if (config.deleteOldVersions) {
            cachePath.toFile().listFiles()?.forEach {
                if (it.toPath() != bundlePath) {
                    logger.info { "deleting old console resource $it" }
                    it.deleteRecursively()
                }
            }
        }

        if (!bundlePath.exists() || config.alwaysDownload) {
            val urlBuilder = URLBuilder(urlString = config.consoleReleaseUrl)
            urlBuilder.appendPathSegments(config.consoleReleaseVersion, config.bundleName)

            val time = measureTime {
                URI(urlBuilder.toString()).toURL().openStream().use { input ->
                    ZipInputStream(input).use { zipInput ->
                        var entry = zipInput.nextEntry

                        while (entry != null) {
                            val filePath = bundlePath.resolve(entry.name)

                            if (entry.isDirectory) {
                                filePath.createDirectory()
                            } else {
                                val file = filePath.toFile()
                                file.parentFile.mkdirs()

                                FileOutputStream(file).use { output ->
                                    zipInput.copyTo(output)
                                }
                            }

                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                    }
                }
            }

            logger.info { "download and extracted console ${config.consoleReleaseVersion} in $time" }
        }

        bundlePath
    } catch (e: Exception) {
        logger.error(e) { "Error setting up console - /ui/console will not be available" }
        return
    }

    staticFiles("console", bundlePath.toFile())
}