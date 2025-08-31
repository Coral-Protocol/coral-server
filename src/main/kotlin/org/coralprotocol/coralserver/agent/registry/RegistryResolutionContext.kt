package org.coralprotocol.coralserver.agent.registry

import com.akuleshov7.ktoml.Toml
import org.coralprotocol.coralserver.config.Config

data class RegistryResolutionContext(
    val serializer: Toml,
    val config: Config
)