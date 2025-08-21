package org.coralprotocol.coralserver.agent.registry

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.inputStream


@Serializable(with = UnresolvedRegistryAgentSerializer::class)
sealed class UnresolvedRegistryAgentReference {
    /**
     * Marketplace agent
     */
    @Serializable
    data class Marketplace(
        val version: String
    ) : UnresolvedRegistryAgentReference()

    /**
     * Local (on the disk) registry agent
     */
    @Serializable
    data class Local(
        val path: String,
    ) : UnresolvedRegistryAgentReference()

    /**
     * Agent on a remote git repository
     */
    @Serializable
    data class Git(
        val git: String,
        val branch: String? = null,
        val tag: String? = null,
        val rev: String? = null,
    ) : UnresolvedRegistryAgentReference()

    fun resolve(toml: Toml): RegistryAgent {
        when (this) {
            is Marketplace -> TODO("marketplace agents not supported yet")
            is Git -> TODO("git agents not supported yet")
            is Local -> {
                try {
                    val agentTomlFile = Path.of(path, "coral-agent.toml")
                    val agent = toml.decodeFromStream<UnresolvedRegistryAgent>(agentTomlFile.inputStream())
                    return agent.resolve()
                }
                catch (e: Exception) {
                    logger.error { "Failed to resolve local agent: $path" }
                    throw e
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
object UnresolvedRegistryAgentSerializer : KSerializer<UnresolvedRegistryAgentReference> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("UnresolvedRegistryAgent", serialDescriptor<UnresolvedRegistryAgentReference.Marketplace>())

    override fun serialize(
        encoder: Encoder,
        value: UnresolvedRegistryAgentReference
    ) {
        throw UnsupportedOperationException("Serialization is not supported")
    }

    override fun deserialize(decoder: Decoder): UnresolvedRegistryAgentReference {
        try {
            return decoder.decodeSerializableValue(UnresolvedRegistryAgentReference.Local.serializer())
        } catch (_: Exception) {

        }

        try {
            return decoder.decodeSerializableValue(UnresolvedRegistryAgentReference.Git.serializer())
        } catch (_: Exception) {

        }

        try {
            /*
             * This works for :
             * 1) marketplace = "0.1.0"
             * and:
             * 2) marketplace = { version = "0.1.1" }
             *
             * ... but I'm not sure if it is designed intentionally to work this way
             */
            return UnresolvedRegistryAgentReference.Marketplace(decoder.decodeString())
        } catch (_: Exception) {

        }

        throw IllegalArgumentException("Unsupported agent format")
    }
}
