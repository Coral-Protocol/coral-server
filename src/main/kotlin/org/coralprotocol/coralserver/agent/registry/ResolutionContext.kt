package org.coralprotocol.coralserver.agent.registry

import java.nio.file.Path

abstract class ResolutionContext {
    abstract val path: Path?
}

data class AgentResolutionContext(
    val registrySourceIdentifier: AgentRegistrySourceIdentifier,
    override val path: Path? = null,
) : ResolutionContext()