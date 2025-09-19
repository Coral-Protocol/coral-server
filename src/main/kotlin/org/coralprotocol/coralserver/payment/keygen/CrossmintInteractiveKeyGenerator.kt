package org.coralprotocol.coralserver.payment.keygen

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import net.peanuuutz.tomlkt.Toml
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.CrossmintBlockchainService
import org.coralprotocol.payment.blockchain.models.SignerConfig
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val logger = KotlinLogging.logger {}

private fun prompt(prompt: String): String {
    println(prompt)
    return readln()
}

private fun promptYN(prompt: String): Boolean {
    val response = prompt("$prompt [y/N]").lowercase()
    return response.startsWith("y")
}

private val toml = Toml {
    ignoreUnknownKeys = false
}


class CrossmintInteractiveKeyGenerator(
    val config: Config,
    val rpcUrl: String,
    val useStaging: Boolean = false
) {
    suspend fun start() {
        if (config.paymentConfig.wallet != null) {
            logger.warn { "There is already a configured wallet, continue?" }
            if (promptYN("Overwrite? [y/N]"))
                return
        }

        val apiKey = prompt("Enter crossmint API key:")
        val keypairPath = Path.of(System.getProperty("user.home"), ".coral", "crossmint-keypair.json")

        val keypairInfo = CrossmintBlockchainService.generateDeviceKeypair(
            keypairPath.absolutePathString(),
            overwriteExisting = true
        ).getOrThrow().let {
            logger.info { "Keypair saved to ${keypairPath.absolutePathString()}" }
            it
        }

        // sign it!
        logger.info { "Key generated!" }
        logger.info { "Please sign the key here: https://paymentlogin.coralprotocol.org/crossmint#pubkey=${keypairInfo.publicKey}" }
        logger.info { "After signing, you will be given a public wallet address" }

        val walletPublicAddress = prompt("Enter your wallet public address from the sign in page:")
        val email = prompt("Enter your crossmint affiliated email:")
        if (promptYN("Save to config file?")) {
            // write new wallet
            val newWallet = Wallet.Crossmint(
                locator = "email:$email:solana-smart-wallet",
                address = walletPublicAddress,
                apiKey = apiKey,
                keypairPath = keypairPath.absolutePathString(),
                staging = useStaging
            )
            val tomlContent = toml.encodeToString(Wallet.serializer(), newWallet)
            File(config.paymentConfig.walletPath).writeText(tomlContent)
        } else {
            logger.info { "No operation performed" }
        }
    }
}

// Equivalent to org.coralprotocol.coralserver.Main.main() with --interactive-keygen
fun main() {
    val config = Config.loadFromFile()
    val keyGen = CrossmintInteractiveKeyGenerator(config, config.paymentConfig.rpcUrl)
    runBlocking {
        keyGen.start()
    }
}