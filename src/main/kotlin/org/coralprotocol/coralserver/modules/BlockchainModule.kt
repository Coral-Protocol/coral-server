package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.payment.BlankBlockchainService
import org.coralprotocol.coralserver.payment.BlankX402Service
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.BlockchainServiceImpl
import org.coralprotocol.payment.blockchain.X402Service
import org.coralprotocol.payment.blockchain.X402ServiceImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val blockchainModule = module {
    singleOf(::JupiterService)

    single<BlockchainService>(createdAtStart = true) {
        val config = get<PaymentConfig>()
        when (val wallet = config.remoteAgentWallet) {
            null -> {
                logger.warn("Agent exporting and importing will be disabled because no wallet was configured")
                BlankBlockchainService()
            }

            else -> BlockchainServiceImpl(
                rpcUrl = config.remoteAgentWallet.rpcUrl,
                commitment = "confirmed",
                signerConfig = wallet.signerConfig
            )
        }
    }

    single<X402Service>(createdAtStart = true) {
        val config = get<PaymentConfig>()
        when (val wallet = config.x402Wallet) {
            null -> {
                logger.warn("x402 service forwarding services will be disabled because no wallet was configured")
                BlankX402Service()
            }

            is Wallet.Solana -> X402ServiceImpl(
                rpcUrl = wallet.rpcUrl,
                commitment = "confirmed",
                signerConfig = wallet.signerConfig
            )

            else -> {
                logger.warn("x402 service forwarding services will be disabled because the configured wallet is not a Solana wallet")
                BlankX402Service()
            }
        }
    }

}