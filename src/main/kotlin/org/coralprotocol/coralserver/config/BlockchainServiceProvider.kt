package org.coralprotocol.coralserver.config

import mu.KotlinLogging
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.BlockchainServiceImpl
import org.coralprotocol.payment.blockchain.models.SignerConfig
import java.io.File

private val logger = KotlinLogging.logger {}

fun logNoPayments() {
    logger.warn { "Payment services will be disabled, meaning:" }
    logger.warn { "- Importing remote agents will be disabled" }
    logger.warn { "- Exporting agents will be disabled" }
}

suspend fun BlockchainService.Companion.loadFromFile(config: Config): BlockchainService? {
    return when (val wallet = config.paymentConfig.wallet) {
        is Wallet.Crossmint -> {
            val rpcUrl = config.paymentConfig.rpcUrl

            val keypair = File(wallet.keypairPath)
            if (keypair.exists()) {
                val signerConfig = SignerConfig.Crossmint(
                    apiKey = wallet.apiKey,
                    walletLocator = wallet.walletLocator,
                    walletAddress = wallet.walletAddress,
                    adminSignerLocator = "dummy",
                )

                val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
                val info = blockchainService.getCrossmintDelegatedKeypair(wallet.keypairPath, false).getOrThrow()

                if (info.createdNew) {
                    logger.warn { "A new keypair was created and must be signed!" }
                    logger.warn { "Sign the keypair here: https://sign.coralprotocol.org/#pubkey=${info.publicKey}"}
                }
            }

            // dummy config
            val signerConfig = SignerConfig.Crossmint(
                apiKey = wallet.apiKey,
                walletLocator = wallet.walletLocator,
                walletAddress = wallet.walletAddress,
                adminSignerLocator = "dummy",
                useStaging = true,
                deviceKeypairPath = wallet.keypairPath
            )

            val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
            blockchainService.getCrossmintDelegatedKeypair(wallet.keypairPath, false).fold(
                onSuccess = {
                    logger.info { "Successfully loaded keypair from ${wallet.keypairPath}" }
                    logger.info { "Public key: ${it.publicKey}" }
                    logger.info { "Wallet address: ${wallet.walletAddress}" }

                    return@loadFromFile blockchainService
                },
                onFailure = {
                    logger.error(it) { "Failed to load keypair from ${wallet.keypairPath}" }
                    logNoPayments()

                    return@loadFromFile null
                }
            )
        }
        else -> {
            logNoPayments()
            null
        }
    }
}