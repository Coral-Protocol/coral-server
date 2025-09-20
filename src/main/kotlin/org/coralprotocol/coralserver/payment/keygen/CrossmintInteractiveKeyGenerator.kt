package org.coralprotocol.coralserver.payment.keygen

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.payment.blockchain.CrossmintBlockchainService
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
    return when {
        response.startsWith("y", ignoreCase = true) -> true
        response.startsWith("n", ignoreCase = true) -> false
        else -> promptYN(response)
    }
}

private val toml = Toml {
    ignoreUnknownKeys = false
}

class CrossmintInteractiveKeyGenerator(
    val config: Config,
    val useStaging: Boolean = false
) {
    fun start() {
        println("Let's get you set up!")
        println("This generator will establish your local keypair to access a delegated Crossmint wallet")
        println("This requires an access key for our closed beta - they should be sent to you via the email you signed up to the beta with.")
        println("If you have any issues, please check our discord: https://discord.gg/UjVZ8TaR")
        println("")
        println("Note that a level of software development experience is currently required for the closed beta." +
                " This isn't yet well suited for first time developers.")


        val apiKey = prompt("Enter crossmint API key:")
        val originalKeypairPath = Path.of(System.getProperty("user.home"), ".coral", "crossmint-keypair.json")

        val walletExistsAlready = config.paymentConfig.wallet != null
        val keypairExistsAlready = originalKeypairPath.toFile().exists()

        val shouldUseTempFile = keypairExistsAlready && (promptYN("Keypair file already exists at ${originalKeypairPath.absolutePathString()}, overwrite?"))
        val possiblyTempKeypairPath = if(shouldUseTempFile) {
            val tempFile = File.createTempFile("coral-crossmint-keypair", ".json")
            tempFile.deleteOnExit()
            tempFile.toPath()
        } else {
            originalKeypairPath
        }
        val keypairInfo = CrossmintBlockchainService.generateDeviceKeypair(
            possiblyTempKeypairPath.absolutePathString(),
            overwriteExisting = false // If there's already an existing that shouldn't be overwritten, it saves to a temp instead
        ).getOrThrow().let {
            logger.info { "Keypair saved to ${originalKeypairPath.absolutePathString()}" }
            it
        }

        // sign it!
        logger.info { "Key generated!" }
        logger.info { "Please sign the key here: https://paymentlogin.coralprotocol.org/crossmint#pubkey=${keypairInfo.publicKey}" }
        logger.info { "After signing, you will be given a public wallet address" }

        val walletPublicAddress = prompt("Enter your wallet public address from the sign in page:")
        val email = prompt("Enter your crossmint affiliated email:")

        val newWallet = Wallet.Crossmint(
            locator = "email:$email:solana-smart-wallet",
            address = walletPublicAddress,
            apiKey = apiKey.decodeBase64String(),
            keypairPath = originalKeypairPath.absolutePathString(),
            staging = useStaging
        )
        val walletTomlContent = toml.encodeToString(Wallet.serializer(), newWallet)


        val saveWalletPrompt = if(walletExistsAlready) {
            "A wallet is already configured at ${config.paymentConfig.walletPath}, overwrite?"
        } else {
            "Save new wallet to ${config.paymentConfig.walletPath}?"
        }

        if (promptYN(saveWalletPrompt)) {
            File(config.paymentConfig.walletPath).writeText(walletTomlContent)
            logger.info { "Wallet saved to ${config.paymentConfig.walletPath}" }
        } else {
            logger.info { "No operation performed" }
        }
        logger.info { "All done! You can now close this program." }
        logger.info { "Keep your keypair file safe - it is required to access your Crossmint wallet!" }
        logger.info { "\n\n Next step: \n\n" }
        logger.info { "Below will be your public wallet address - to start trying out paid agents, it will need funding" }
        println("Public wallet address: $walletPublicAddress")
        logger.info { "Please reach out to the team to have this funded" }

        if(promptYN("Print wallet config to console?")) {
            println("\n\n")
            println(walletTomlContent)
            println("\n\n")
        }

        if(promptYN("Print generated keypair content to console? (for convenient copy paste, don't share this with others)")) {
            println("\n\n")
            val generatedKeypairContent = File(possiblyTempKeypairPath.toAbsolutePath().toString()).readText()
            println(generatedKeypairContent)
            println("\n\n")
        }
    }
}

// Equivalent to org.coralprotocol.coralserver.Main.main() with --interactive-keygen
fun main() {
    val config = Config.loadFromFile(silent = true)
    val keyGen = CrossmintInteractiveKeyGenerator(config)
    runBlocking {
        keyGen.start()
    }
}