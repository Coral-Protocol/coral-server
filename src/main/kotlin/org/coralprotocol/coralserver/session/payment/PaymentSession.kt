package org.coralprotocol.coralserver.session.payment

/**
 * Importing-side
 */
data class PaymentSession(
    /**
     * Blockchain session ID
     */
    val sessionId: Long,

    /**
     * CREATED or FUNDED
     * TODO: Remove
     */
    val status: String,

    /**
     * Agent IDs that are available
     * TODO: Remove
     */
    val availableAgents: List<String>? = null,

    /**
     * // Optional error message
     * TODO: Remove
     */
    val error: String? = null,

    //TODO: Remove
    val transactionSignature: String,
    //TODO: Remove
    val fundingRequired: Boolean,
)