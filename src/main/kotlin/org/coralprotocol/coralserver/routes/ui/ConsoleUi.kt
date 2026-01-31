package org.coralprotocol.coralserver.routes.ui

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.ConsoleConfig
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.measureTime

fun Route.consoleUi() {
    val logger by inject<Logger>(named(LOGGER_ROUTES))
    val consoleConfig by inject<ConsoleConfig>()
    val networkConfig by inject<NetworkConfig>()

    if (!consoleConfig.enabled) {
        logger.info { "/ui/console disabled by config" }
        return
    }

    val bundlePath = try {
        val allowedParent = Path.of(System.getProperty("user.home"), ".coral")
            .toAbsolutePath().normalize()

        val cachePath = Path.of(consoleConfig.cachePath).toAbsolutePath().normalize()
        require(cachePath.startsWith(allowedParent)) {
            "cachePath must be under $allowedParent, got $cachePath"
        }

        val bundlePath = cachePath.resolve(consoleConfig.consoleReleaseVersion).normalize()
        require(bundlePath.startsWith(cachePath)) {
            "consoleReleaseVersion contains path traversal: ${consoleConfig.consoleReleaseVersion}"
        }

        if (!cachePath.exists())
            cachePath.createDirectories()

        if (consoleConfig.deleteOldVersions) {
            cachePath.toFile().listFiles()?.forEach {
                if (it.toPath().toAbsolutePath().normalize() != bundlePath) {
                    logger.info { "deleting old console resource $it" }
                    it.deleteRecursively()
                }
            }
        }

        if (!bundlePath.exists() || consoleConfig.alwaysDownload) {
            val urlBuilder = URLBuilder(urlString = consoleConfig.consoleReleaseUrl)
            urlBuilder.appendPathSegments(consoleConfig.consoleReleaseVersion, consoleConfig.bundleName)

            val time = measureTime {
                URI(urlBuilder.toString()).toURL().openStream().use { input ->
                    ZipInputStream(input).use { zipInput ->
                        var entry = zipInput.nextEntry

                        while (entry != null) {
                            val filePath = bundlePath.resolve(entry.name).normalize()
                            require(filePath.startsWith(bundlePath)) {
                                "Zip entry contains path traversal: ${entry.name}"
                            }

                            if (entry.isDirectory) {
                                filePath.createDirectories()
                            } else {
                                filePath.parent?.createDirectories()
                                FileOutputStream(filePath.toFile()).use { output ->
                                    zipInput.copyTo(output)
                                }
                            }

                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                        }
                    }
                }
            }

            logger.info { "downloaded and extracted console ${consoleConfig.consoleReleaseVersion} in $time" }
        }

        bundlePath
    } catch (e: Exception) {
        logger.error(e) { "Error setting up console - /ui/console will not be available" }
        return
    }

    staticFiles("console", bundlePath.toFile())
    // The console requires a secure context, which is not available from 0.0.0.0
    // see https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Secure_Contexts
    val consoleServingHost = "localhost"
    logger.info {
        "\n\n For Coral console, navigate to http://$consoleServingHost:${networkConfig.bindPort}/ui/console\n" +
        " Login using an API key matching one set in auth.keys in the server configuration.\n"
    }

}