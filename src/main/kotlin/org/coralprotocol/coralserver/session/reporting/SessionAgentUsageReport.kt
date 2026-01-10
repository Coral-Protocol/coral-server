package org.coralprotocol.coralserver.session.reporting

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.models.Telemetry

@Serializable
data class SessionAgentUsageReport(
    val name: UniqueAgentName,
    val registryIdentifier: RegistryAgentIdentifier,
    val startTime: Long,
    val endTime: Long,
    val telemetry: List<Telemetry> = emptyList()
)
