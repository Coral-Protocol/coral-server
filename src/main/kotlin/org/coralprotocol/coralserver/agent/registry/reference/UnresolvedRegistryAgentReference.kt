package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext

const val AGENT_FILE = "coral-agent.toml"

@Serializable(with = UnresolvedRegistryAgentSerializer::class)
sealed interface UnresolvedRegistryAgentReference {
    fun resolve(
        context: RegistryResolutionContext,
        name: String
    ): RegistryAgent
}

@OptIn(ExperimentalSerializationApi::class)
object UnresolvedRegistryAgentSerializer : KSerializer<UnresolvedRegistryAgentReference> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("UnresolvedRegistryAgent", serialDescriptor<NameReference>())

    override fun serialize(
        encoder: Encoder,
        value: UnresolvedRegistryAgentReference
    ) {
        throw UnsupportedOperationException("Serialization is not supported")
    }

    override fun deserialize(decoder: Decoder): UnresolvedRegistryAgentReference {
        try {
            return decoder.decodeSerializableValue(LocalReference.serializer())
        } catch (_: Exception) {

        }

        try {
            return decoder.decodeSerializableValue(GitReference.serializer())
        } catch (_: Exception) {

        }

        try {
            return decoder.decodeSerializableValue(NameReference.serializer())
        } catch (_: Exception) {

        }

        try {
            // This will decode the syntax:
            // ```toml
            // [agent-import]
            // agent = "1.0.0"
            // ```
            return NameReference(decoder.decodeString(), "coral")
        } catch (_: Exception) {

        }

        throw IllegalArgumentException("Unsupported agent format")
    }
}
