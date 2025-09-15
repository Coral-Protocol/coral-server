package org.coralprotocol.coralserver.config

import mu.KotlinLogging
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.BlockchainServiceImpl
import org.coralprotocol.payment.blockchain.models.SignerConfig
import java.io.File
import java.nio.file.Path

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

            val resolvedKeypairPath = run {
                if (Path.of(wallet.keypairPath).isAbsolute) {
                    File(wallet.keypairPath)
                }
                else {
                    // Local to wallet config takes priority
                    val walletPath = Path.of(config.paymentConfig.walletPath)
                    if (walletPath.parent != null) {
                        val keypair = walletPath.parent.resolve(wallet.keypairPath).toFile()
                        if (keypair.exists()) {
                            return@run keypair
                        }
                    }

                    // If there's an existing working directory keypair, we can use that
                    val workingDir = File(wallet.keypairPath)
                    if (workingDir.exists()) {
                        return@run workingDir
                    }

                    // Nothing exists, default location priority is wallet-relative then working directory if we can't
                    // get the wallet relative path
                    if (walletPath.parent != null) {
                        walletPath.parent.resolve(wallet.keypairPath).toFile()
                    }
                    else {
                        File(wallet.keypairPath)
                    }
                }
            }

            if (!resolvedKeypairPath.exists()) {
                val signerConfig = SignerConfig.Crossmint(
                    apiKey = wallet.apiKey,
                    walletLocator = wallet.locator,
                    walletAddress = wallet.address,
                    adminSignerLocator = "dummy",
                    useStaging = wallet.staging
                )

                val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
                val info = blockchainService.getCrossmintDelegatedKeypair(resolvedKeypairPath.toString(), false).getOrThrow()

                if (info.createdNew) {
                    logger.warn { "A new keypair was created and must be signed!" }
                    logger.warn { "Sign the keypair here: https://sign.coralprotocol.org/#pubkey=${info.publicKey}"}
                }
            }

            // dummy config
            val signerConfig = SignerConfig.Crossmint(
                apiKey = wallet.apiKey,
                walletLocator = wallet.locator,
                walletAddress = wallet.address,
                adminSignerLocator = "dummy",
                useStaging = wallet.staging,
                deviceKeypairPath = resolvedKeypairPath.toString(),
            )

            val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
            blockchainService.getCrossmintDelegatedKeypair(resolvedKeypairPath.toString(), false).fold(
                onSuccess = {
                    logger.info { "Successfully loaded keypair from $resolvedKeypairPath" }
                    logger.info { "Wallet address: ${wallet.address}" }

                    return@loadFromFile blockchainService
                },
                onFailure = {
                    logger.error(it) { "Failed to load keypair from $resolvedKeypairPath" }
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