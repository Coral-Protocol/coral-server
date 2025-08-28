package org.coralprotocol.coralserver.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// TODO: Applications are a work in progress. This is safe to ignore for now.

/**
 * Main application configuration.
 */
@Serializable
data class Config(
    val applications: List<ApplicationConfig> = emptyList(),
    val applicationSource: ApplicationSourceConfig? = null
)

/**
 * Configuration for an application.
 */
@Serializable
data class ApplicationConfig(
    val id: String,
    val name: String,
    val description: String = "",

    @SerialName("privacy_keys")
    val privacyKeys: List<String> = emptyList()
)

/**
 * Configuration for application source (for future use).
 */
@Serializable
data class ApplicationSourceConfig(
    val type: String,
    val url: String? = null,

    @SerialName("refresh_interval_seconds")
    val refreshIntervalSeconds: Int = 3600
)