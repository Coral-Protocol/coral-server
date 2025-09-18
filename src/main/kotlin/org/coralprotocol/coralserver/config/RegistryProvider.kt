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
import kotlin.io.path.exists
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {  }
private const val REGISTRY_FILE = "registry.toml"

private data class RegistrySource(
    val stream: InputStream?,
    val path: Path,
    val resource: Boolean
)

private fun registrySource(): RegistrySource {
    return when (val path = System.getenv("REGISTRY_FILE_PATH")) {
        null -> RegistrySource(
            stream = Main::class.java.classLoader.getResource(REGISTRY_FILE)?.openStream(),
            path = Path.of(System.getProperty("user.dir")),
            resource = true
        )
        else -> {
            val path = Path.of(path)
            val file = path.toFile()

            RegistrySource(
                stream = if (file.exists()) file.inputStream() else null,
                path =  path.parent,
                resource = false
            )
        }
    }
}

fun AgentRegistry.Companion.loadFromFile(config: Config): AgentRegistry {
    val source = registrySource()

    try {
        if (source.stream == null) {
            throw FileNotFoundException("Registry file not found")
        }

        var registry: AgentRegistry
        val time = measureTimeMillis {
            val unresolved = toml.decodeFromNativeReader<UnresolvedAgentRegistry>(source.stream.reader())
            val context = RegistryResolutionContext(
                serializer = toml,
                config = config,
                path = source.path
            )

            registry = unresolved.resolve(context)
        }
        logger.info { "Loaded registry file in $time ms" }

        return registry
    }
    catch (e: Exception) {
        val identifier = if (source.resource) {
            "<built-in resource>"
        }
        else {
            source.path.toFile().name
        }

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