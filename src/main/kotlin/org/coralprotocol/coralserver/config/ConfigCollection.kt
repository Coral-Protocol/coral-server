package org.coralprotocol.coralserver.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.source.decodeFromStream
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.files.FileNotFoundException
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentRegistry
import java.nio.file.*
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger {}

/**
 * Creates a flow WatchEvent from a watchService
 */
fun WatchService.eventFlow(): Flow<List<WatchEvent<out Any>>> = flow {
    while (currentCoroutineContext().isActive) {
        coroutineScope {
            var key: WatchKey? = null
            val job = launch {
                runInterruptible(Dispatchers.IO) {
                    key = take()
                }
            }
            job.join()
            val currentKey = key
            if (currentKey != null) {
                emit(currentKey.pollEvents())
                currentKey.reset()
            }
        }
    }
}

/**
 * Returns a flow with the files inside a folder (with a given glob)
 */
fun Path.listDirectoryEntriesFlow(glob: String): Flow<List<Path>> {
    val watchService = watch()
    return watchService.eventFlow()
        .map { listDirectoryEntries(glob) }
        .onStart { emit(listDirectoryEntries(glob)) }
        .onCompletion { watchService.close() }
        .flowOn(Dispatchers.IO)
}

/**
 * Creates a new WatchService for any Event
 */
fun Path.watch(): WatchService {
    return watch(
        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.OVERFLOW, StandardWatchEventKinds.ENTRY_DELETE
    )
}

/**
 * Creates a new watch service
 */
fun Path.watch(vararg events: WatchEvent.Kind<out Any>) =
    fileSystem.newWatchService()!!.apply { register(this, events) }


/**
 * Loads application configuration from resources.
 */
class ConfigCollection(
    val appConfigPath: Path? = getConfigPath("application.yaml"),
    val registryPath: Path? = getConfigPath("registry.toml"),
    val defaultConfig: AppConfig = AppConfig(
        applications = listOf(
            ApplicationConfig(
                id = "default-app",
                name = "Default Application",
                description = "Default application (fallback)",
                privacyKeys = listOf("default-key", "public")
            )
        )
    ),
    val defaultRegistry: AgentRegistry = AgentRegistry(
        mapOf(),
        mapOf()
    )
) {
    var appConfig: AppConfig = loadAppConfig(appConfigPath)
        private set

    var registry: AgentRegistry = loadRegistry(registryPath)
        private set

    private val watchJob: Job? = appConfigPath?.let {
        CoroutineScope(Dispatchers.Default).launch {
            logger.info{ "Watching for config changes in '${it.parent}'..." }
            it.parent.listDirectoryEntriesFlow("application.yaml*").distinctUntilChanged().collect {
                logger.info { "application.yaml changed. Reloading..." }
                appConfig = loadAppConfig(appConfigPath)
            }
        }
    }

    fun stopWatch() {
        watchJob?.cancel()
    }

    companion object {
        private fun getConfigPath(file: String): Path? {
            // Try to load from resources if no config path set
            return when (val configPath = System.getenv("CONFIG_PATH")) {
                null -> if(Path.of("./$file").toFile().exists()) {
                    Path.of("./$file") // Check local application.yaml
                } else Path.of("./src/main/resources/$file") // Assume running from source when config path not specified

                else -> (Path.of(configPath, file))
            }
        }
    }

    /**
     * Loads the application configuration from the resources.
     * If the configuration is already loaded, returns the cached instance.
     */
    private fun loadAppConfig(path: Path?): AppConfig = try {
        val file = path?.toFile()
        if (file != null) {
            if (!file.exists()) {
                throw FileNotFoundException(file.absolutePath)
            }

            val c =
                Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property)).decodeFromStream<AppConfig>(
                    file.inputStream()
                )
            appConfig = c

            logger.info { "Loaded configuration with ${c.applications.size} applications" }
            c
        } else {
            throw Exception("Failed to lookup resource.")
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to load configuration, using default" }
        defaultConfig
    }

    /**
     * Loads the agent registry from the specified path.
     */
    private fun loadRegistry(path: Path?): AgentRegistry = try {
        val file = path?.toFile()
        if (file != null) {
            if (!file.exists()) {
                throw FileNotFoundException(file.absolutePath)
            }

            val toml = Toml(
                inputConfig = TomlInputConfig(
                    // allow/prohibit unknown names during the deserialization, default false
                    ignoreUnknownNames = true,
                    // allow/prohibit empty values like "a = # comment", default true
                    allowEmptyValues = true,
                    // allow/prohibit null values like "a = null", default true
                    allowNullValues = true,
                    // allow/prohibit escaping of single quotes in literal strings, default true
                    allowEscapedQuotesInLiteralStrings = true,
                    // allow/prohibit processing of empty toml, if false - throws an InternalDecodingException exception, default is true
                    allowEmptyToml = true,
                    // allow/prohibit default values during the deserialization, default is false
                    ignoreDefaultValues = false,
                )
            )

            val reg = toml.decodeFromStream<UnresolvedAgentRegistry>(file.inputStream())
                .resolve(toml)

            logger.info { "Loaded registry with ${reg.importedAgents.size} imported agents and ${reg.exportedAgents.size} exported agents" }
            reg
        } else {
            throw Exception("Failed to load registry file")
        }
    }
    catch (e: RegistryException) {
        logger.error{ "Error with registry file: ${e.message}" }
        logger.warn{ "Using default registry" }
        defaultRegistry
    }
    catch (e: Exception) {
        logger.error(e) { "Unexpected exception loading registry" }
        logger.warn{ "Using default registry" }
        defaultRegistry
    }

    /**
     * Validates if the application ID and privacy key are valid.
     */
    fun isValidApplication(applicationId: String, privacyKey: String): Boolean {
        val application = appConfig.applications.find { it.id == applicationId }
        return application != null && application.privacyKeys.contains(privacyKey)
    }

    /**
     * Gets an application by ID.
     */
    fun getApplication(applicationId: String): ApplicationConfig? {
        return appConfig.applications.find { it.id == applicationId }
    }
}

fun ConfigCollection.Companion.custom(config: AppConfig) = ConfigCollection(defaultConfig = config)
