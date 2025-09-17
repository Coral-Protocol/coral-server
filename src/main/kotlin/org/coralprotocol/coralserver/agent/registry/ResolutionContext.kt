package org.coralprotocol.coralserver.agent.registry

import net.peanuuutz.tomlkt.Toml
import org.coralprotocol.coralserver.config.Config
import java.nio.file.Path
import kotlin.io.path.exists

abstract class ResolutionContext() {
    abstract val path: Path

    /**
     * Tries to resolve a local path.  If the path does not exist, the original path will be returned.
     */
    fun tryRelative(other: Path): Path {
        val relative = path.resolve(other)
        return if (relative.exists()) {
            relative
        }
        else {
            other
        }
    }
}

data class RegistryResolutionContext(
    val serializer: Toml,
    val config: Config,
    override val path: Path
) : ResolutionContext()

data class AgentResolutionContext(
    val registryResolutionContext: RegistryResolutionContext,
    override val path: Path
) : ResolutionContext()