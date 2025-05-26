package org.coralprotocol.coralserver.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import com.sksamuel.hoplite.ConfigLoader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.io.files.FileNotFoundException
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Loads application configuration from resources.
 */
object AppConfigLoader {
    private var config: AppConfig? = null

    /**
     * Loads the application configuration from the resources.
     * If the configuration is already loaded, returns the cached instance.
     */
    fun loadConfig(): AppConfig {
        // TODO (alan): redo all this
        val _c = config
        if (_c != null) {
            return _c
        }
        val config = try {
            // Try to load from resources
            val resourcePath = "application.yaml"
            val resource = AppConfigLoader::class.java.classLoader.getResource(resourcePath)
            if (resource != null) {
                val file = File(resource.path)
                if (!file.exists()) {
                    throw FileNotFoundException(file.absolutePath)
                }

                val c =
                    Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property)).decodeFromStream<AppConfig>(
                        file.inputStream()
                    )
                config = c

                logger.info { "Loaded configuration with ${c.applications.size ?: 0} applications & ${c.registry?.size ?: 0} registry agents" }
                c
            } else {
                throw Exception("Resource not found: $resourcePath")
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load configuration, using default" }
            AppConfig(
                applications = listOf(
                    ApplicationConfig(
                        id = "default-app",
                        name = "Default Application",
                        description = "Default application (fallback)",
                        privacyKeys = listOf("default-key", "public")
                    )
                )
            )
        }
        this.config = config
        return config
    }

    /**
     * Validates if the application ID and privacy key are valid.
     */
    fun isValidApplication(applicationId: String, privacyKey: String): Boolean {
        val config = loadConfig()
        val application = config.applications.find { it.id == applicationId }
        return application != null && application.privacyKeys.contains(privacyKey)
    }

    /**
     * Gets an application by ID.
     */
    fun getApplication(applicationId: String): ApplicationConfig? {
        val config = loadConfig()
        return config.applications.find { it.id == applicationId }
    }
}
