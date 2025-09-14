@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface Wallet {
    /**
     * Address that can be used to resolve the public wallet address
     */
    val walletLocator: String

    /**
     * Public wallet address, in a future version of coral-escrow this can be derived from walletLocator
     */
    val walletAddress: String

    @SerialName("crossmint")
    data class Crossmint(
        override val walletLocator: String,
        override val walletAddress: String,
        val apiKey: String,
        val keypairPath: String
    ) : Wallet
}