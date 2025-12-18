package org.coralprotocol.coralserver.session.reporting

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier

@Serializable
data class SessionAgentUsageReport(
    val name: UniqueAgentName,
    val registryIdentifier: RegistryAgentIdentifier,
    val startTime: Long,
    val endTime: Long
    // todo: claims made
)
