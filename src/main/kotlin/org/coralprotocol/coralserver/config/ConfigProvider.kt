package org.coralprotocol.coralserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.Main
import org.coralprotocol.coralserver.util.isWindows
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {  }
private const val CONFIG_FILE = "config.toml"

private fun configSource(): Pair<InputStream?, String> {
    return when (val path = System.getenv("CONFIG_FILE_PATH")) {
        null -> Pair(Main::class.java.classLoader.getResource(CONFIG_FILE)?.openStream(), "<built-in resource>")
        else -> Pair(Path.of(path).toFile().inputStream(), path)
    }
}

fun Config.Companion.loadFromFile(silent: Boolean = false): Config {
    val (stream, identifier) = configSource()

    try {
        if (stream == null) {
            throw FileNotFoundException("Config file not found")
        }

        var config: Config
        val decodeTime = measureTimeMillis {
            config = toml.decodeFromNativeReader<Config>(stream.reader())
        }
        if(!silent) logger.info { "Loaded config file in $decodeTime ms" }

        val indexUpdateTime = measureTimeMillis {
            config.updateIndexes()
        }
        if(!silent) logger.info { "Updated indexes in $indexUpdateTime ms" }

        // 😡
        if (!isWindows() && config.dockerConfig.address == "172.17.0.1" && !silent) {
            logger.warn { "The configured docker address ${config.dockerConfig.address} is not reliable" }
            logger.warn { "See https://stackoverflow.com/questions/48546124/what-is-the-linux-equivalent-of-host-docker-internal/67158212#67158212" }
        }

        return config
    }
    catch (e: Exception) {
        if(!silent) {
            logger.error(e) { "Failed to load config file $identifier" }
            logger.warn { "Using a default config" }
        }

        return Config()
    }
}