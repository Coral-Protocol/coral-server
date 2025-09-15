package org.coralprotocol.coralserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.Main
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentRegistry
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {  }
private const val REGISTRY_FILE = "registry.toml"

private fun registrySource(): Pair<InputStream?, String> {
    return when (val path = System.getenv("REGISTRY_FILE_PATH")) {
        null -> Pair(Main::class.java.classLoader.getResource(REGISTRY_FILE)?.openStream(), "<built-in resource>")
        else -> Pair(Path.of(path).toFile().inputStream(), path)
    }
}


fun AgentRegistry.Companion.loadFromFile(config: Config): AgentRegistry {
    val (stream, identifier) = registrySource()

    try {
        if (stream == null) {
            throw FileNotFoundException("Registry file not found")
        }

        var registry: AgentRegistry
        val time = measureTimeMillis {
            val unresolved = toml.decodeFromNativeReader<UnresolvedAgentRegistry>(stream.reader())
            val context = RegistryResolutionContext(
                serializer = toml,
                config = config
            )

            registry = unresolved.resolve(context)
        }
        logger.info { "Loaded registry file in $time ms" }

        return registry
    }
    catch (e: Exception) {

        // RegistryExceptions are well formatted and only need their message printed
        if (e is RegistryException) {
            logger.error { "Failed to load registry file $identifier: ${e.message}" }
        }
        else {
            logger.error(e) { "Failed to load registry file $identifier" }
        }

        logger.warn { "Using a default empty registry" }

        return AgentRegistry()
    }
}