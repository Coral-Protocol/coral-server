package org.coralprotocol.coralserver.agent.registry

import java.nio.file.Path
import kotlin.io.path.exists

abstract class ResolutionContext {
    abstract val path: Path

    /**
     * Tries to resolve a local path.  If the path does not exist, the original path will be returned.
     */
    fun tryRelative(other: Path): Path {
        val relative = path.resolve(other)
        return if (relative.exists()) {
            relative
        } else {
            other
        }
    }
}

data class AgentResolutionContext(
    val registrySourceIdentifier: AgentRegistrySourceIdentifier,
    override val path: Path
) : ResolutionContext()