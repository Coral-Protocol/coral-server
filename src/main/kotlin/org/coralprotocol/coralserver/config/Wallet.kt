@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.config

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

enum class SolanaCluster(val rpcUrl: String) {
    MAIN_NET("https://api.mainnet-beta.solana.com"),
    DEV_NET("https://api.devnet.solana.com"),
    TEST_NET("https://api.testnet.solana.com");
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface Wallet {
    /**
     * This server reports this address to other servers to receive funds.  In the case of delegated wallets, the
     * keypair might not contain the public key that should receive funds, so it is always separated out into this field
     */
    @SerialName("public_key")
    val publicKey: String

    val rpcUrl: String
    val name: String

    @Serializable
    @Suppress("SpellCheckingInspection")
    @SerialName("crossmint-solana")
    data class CrossmintSolana(
        override val name: String,
        val cluster: SolanaCluster = SolanaCluster.MAIN_NET,

        @SerialName("crossmint_api_key")
        val apiKey: String,

        @SerialName("keypair_path")
        val keypairPath: String,

        @SerialName("public_key")
        override val publicKey: String,
    ) : Wallet {
        override val rpcUrl: String = cluster.rpcUrl
    }

    @Serializable
    @SerialName("solana")
    data class Solana(
        override val name: String,
        val cluster: SolanaCluster = SolanaCluster.MAIN_NET,

        @SerialName("keypair_path")
        val keypairPath: String,

        @SerialName("public_key")
        override val publicKey: String,
    ) : Wallet {
        override val rpcUrl: String = cluster.rpcUrl
    }
}