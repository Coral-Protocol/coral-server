package org.coralprotocol.coralserver.config

import mu.KotlinLogging
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.BlockchainServiceImpl
import org.coralprotocol.payment.blockchain.X402ServiceImpl
import org.coralprotocol.payment.blockchain.models.SignerConfig

private val logger = KotlinLogging.logger {}

class BlockchainServiceProvider(val config: PaymentConfig) {
    val blockchainService: BlockchainService? = if (config.remoteAgentWallet != null) {
        BlockchainServiceImpl(
            rpcUrl = config.remoteAgentWallet.rpcUrl,
            commitment = "confirmed",
            signerConfig = getSignerConfig(config.remoteAgentWallet)
        )
    }
    else {
        logger.warn { "Agent exporting and importing will be disabled because no wallet was configured" }
        null
    }

    val x402Service: X402ServiceImpl? = if (config.x402Wallet != null) {
        if (config.x402Wallet !is Wallet.Solana) {
            logger.warn { "x402 service forwarding services will be disabled because the configured wallet is not a Solana wallet" }
            null
        }
        else {
            X402ServiceImpl(
                rpcUrl = config.x402Wallet.rpcUrl,
                commitment = "confirmed",
                signerConfig = getSignerConfig(config.x402Wallet)
            )
        }
    }
    else {
        logger.warn { "x402 service forwarding services will be disabled because no wallet was configured" }
        null
    }

    fun getSignerConfig(wallet: Wallet): SignerConfig {
        return when (wallet) {
            is Wallet.CrossmintSolana -> {
                SignerConfig.Crossmint(
                    apiKey = wallet.apiKey,
                    walletAddress = wallet.walletAddress,
                    useStaging = wallet.cluster != SolanaCluster.MAIN_NET,
                    deviceKeypairPath = wallet.keypairPath
                )
            }
            is Wallet.Solana -> {
                SignerConfig.File(
                    path = wallet.keypairPath
                )
            }
        }
    }
}
