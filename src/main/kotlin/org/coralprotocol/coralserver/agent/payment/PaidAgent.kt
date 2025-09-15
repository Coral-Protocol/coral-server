package org.coralprotocol.coralserver.agent.payment

import coral.escrow.v1.CoralEscrow
import coral.escrow.v1.CoralEscrow.AgentConfig.newBuilder
import kotlinx.serialization.Serializable

@Serializable
data class PaidAgent(
    val id: String,
    val cap: Long,

    /**
     * Simplified: a developer is both signer and recipient
     */
    val developer: String,

    /**
     * For notifications
     */
    val endpoint: String? = null
) {
    fun toBlockchainModel(): CoralEscrow.AgentConfig = newBuilder()
        .setId(id)
        .setCap(cap)
        .setDeveloper(developer)
        .build()
}