package org.coralprotocol.coralserver.config

data class DebugConfig(
    /**
     * Additional environment variables to set for all agents run with a Docker runtime
     */
    val additionalDockerEnvironment: Map<String, String> = mapOf(),

    /**
     * Additional environment variables to set for all agents with an executable runtime
     */
    val additionalExecutableEnvironment: Map<String, String> = mapOf(),
)