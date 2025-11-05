package org.coralprotocol.coralserver.x402

import kotlinx.serialization.Serializable

@Serializable
data class X402ProxiedResponse(
    val code: Int,
    val body: String,
)

