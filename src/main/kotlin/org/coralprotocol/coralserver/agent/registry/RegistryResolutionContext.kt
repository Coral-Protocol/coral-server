package org.coralprotocol.coralserver.agent.registry

import net.peanuuutz.tomlkt.Toml
import org.coralprotocol.coralserver.config.Config

data class RegistryResolutionContext(
    val serializer: Toml,
    val config: Config
)