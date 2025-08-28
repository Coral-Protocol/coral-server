package org.coralprotocol.coralserver.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val applications: List<ApplicationConfig> = emptyList(),
    val applicationSource: ApplicationSourceConfig? = null
)

@Serializable
data class ApplicationConfig(
    val id: String,
    val name: String,
    val description: String = "",

    @SerialName("privacy_keys")
    val privacyKeys: List<String> = emptyList()
)

@Serializable
data class ApplicationSourceConfig(
    val type: String,
    val url: String? = null,

    @SerialName("refresh_interval_seconds")
    val refreshIntervalSeconds: Int = 3600
)