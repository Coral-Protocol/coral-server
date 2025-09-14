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
    @SerialName("locator")
    val locator: String

    /**
     * Public wallet address, in a future version of coral-escrow this can be derived from walletLocator
     */
    val address: String

    @Serializable
    @SerialName("crossmint")
    data class Crossmint(
        override val locator: String,
        override val address: String,

        @SerialName("crossmint_api_key")
        val apiKey: String,

        @SerialName("keypair_path")
        val keypairPath: String,

        @SerialName("staging")
        val staging: Boolean = false,
    ) : Wallet
}