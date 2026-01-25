@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session.reporting

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class SessionAgentUsageReport(
    val name: UniqueAgentName,
    val registryIdentifier: RegistryAgentIdentifier,

    @Serializable(with = InstantSerializer::class)
    val startTime: Instant,

    @Serializable(with = InstantSerializer::class)
    val endTime: Instant,
    // todo: claims made
)
