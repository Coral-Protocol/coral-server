package org.coralprotocol.coralserver.agent.execution

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionConfig(
    @Description("The minimum isolation boundary the agent requires to run safely")
    @SerialName("min_isolation")
    val minIsolation: MinIsolation,

    @Description("Explicit non-Coral external hosts the agent needs to reach")
    @Optional
    val network: NetworkDeclaration = NetworkDeclaration(),
)

@Serializable
enum class MinIsolation {
    @SerialName("process") PROCESS,
    @SerialName("container") CONTAINER,
}

@Serializable
data class NetworkDeclaration(
    @Optional
    @SerialName("external_hosts")
    val externalHosts: Set<String> = emptySet(),
)
