package org.coralprotocol.coralserver.config

import com.sksamuel.hoplite.ConfigLoader
import io.github.oshai.kotlinlogging.KotlinLogging

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
        if (config == null) {
            try {
                // Try to load from resources
                val resourcePath = "application.yaml"
                val resource = AppConfigLoader::class.java.classLoader.getResource(resourcePath)

                if (resource != null) {
                    config = ConfigLoader().loadConfigOrThrow<AppConfig>(resource.path)
                    logger.info { "Loaded configuration with ${config?.applications?.size ?: 0} applications" }
                } else {
                    throw Exception("Resource not found: $resourcePath")
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load configuration, using default" }
                config = AppConfig(
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
        }
        return config!!
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
