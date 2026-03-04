package org.coralprotocol.coralserver.config

import kotlinx.serialization.Transient

const val CORAL_MAINNET_MINT = "CoRAitPvr9seu5F9Hk39vbjqA1o1XuoryHjSk1Z1q2mo"
const val CORAL_DEV_NET_MINT = "FBrR4v7NSoEdEE9sdRN1aE5yDeop2cseaBbfPVbJmPhf"

data class PaymentConfig(
    /**
     * A list of all configured wallets
     */
    val wallets: List<Wallet> = listOf(),

    /**
     * The number of times the exporting server should retry getting the session from the blockchain.  This is a safety
     * feature in case there are
     */
    val sessionRetryCount: UInt = 10u,

    /**
     * The delay between retries when trying to get a session
     */
    val sessionRetryDelay: ULong = 1000u,

    /**
     * The name of the wallet to use for remote agent payments
     */
    val remoteAgentWalletName: String? = null,

    /**
     * The name of the wallet to use for x402 wallet payments
     */
    val x402WalletName: String? = null,

    @Transient
    val remoteAgentWallet: Wallet? = wallets.firstOrNull { it.name == remoteAgentWalletName },

    @Transient
    val x402Wallet: Wallet? = wallets.firstOrNull { it.name == remoteAgentWalletName },
)