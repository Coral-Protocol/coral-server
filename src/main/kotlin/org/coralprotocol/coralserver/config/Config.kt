package org.coralprotocol.coralserver.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Network(
    @SerialName("bind_address")
    val bindAddress: String = "0.0.0.0",

    @SerialName("bind_port")
    val bindPort: Int = 5555,
)

@Serializable
data class Config(
    val network: Network = Network(),
)