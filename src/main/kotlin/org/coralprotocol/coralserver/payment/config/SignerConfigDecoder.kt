package org.coralprotocol.coralserver.payment.config



import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import org.coralprotocol.payment.blockchain.models.SignerConfig
import kotlin.reflect.KType

class SignerConfigDecoder : Decoder<SignerConfig> {
    override fun decode(
        node: Node,
        type: KType,
        context: DecoderContext
    ): ConfigResult<SignerConfig> {
        return when (node) {
            is MapNode -> {
                val typeNode = node["type"]
                when (typeNode) {
                    is StringNode -> {
                        when (typeNode.value.uppercase()) {
                            "FILE" -> {
                                val pathNode = node["filePath"]
                                when (pathNode) {
                                    is StringNode -> SignerConfig.File(pathNode.value).valid()
                                    else -> ConfigFailure.Generic("Missing filePath for FILE signer").invalid()
                                }
                            }
                            "ENVIRONMENT", "ENV" -> {
                                val varNode = node["envVar"]
                                when (varNode) {
                                    is StringNode -> SignerConfig.Environment(varNode.value).valid()
                                    null -> SignerConfig.Environment().valid() // Use default
                                    else -> ConfigFailure.Generic("Invalid envVar for ENVIRONMENT signer").invalid()
                                }
                            }
                            "CROSSMINT" -> {
                                val apiKey = (node["apiKey"] as? StringNode)?.value
                                val walletLocator = (node["walletLocator"] as? StringNode)?.value
                                val walletAddress = (node["walletAddress"] as? StringNode)?.value
                                val adminSignerLocator = (node["adminSignerLocator"] as? StringNode)?.value
                                
                                if (apiKey != null && walletLocator != null && 
                                    walletAddress != null && adminSignerLocator != null) {
                                    SignerConfig.Crossmint(
                                        apiKey, walletLocator, walletAddress, adminSignerLocator
                                    ).valid()
                                } else {
                                    ConfigFailure.Generic(
                                        "Missing required fields for CROSSMINT signer"
                                    ).invalid()
                                }
                            }
                            else -> ConfigFailure.Generic("Unknown signer type: ${typeNode.value}").invalid()
                        }
                    }
                    else -> ConfigFailure.Generic("Missing 'type' field for signer").invalid()
                }
            }
            else -> ConfigFailure.Generic("Signer config must be a map").invalid()
        }
    }

    override fun supports(type: KType): Boolean = 
        type.classifier == SignerConfig::class
}