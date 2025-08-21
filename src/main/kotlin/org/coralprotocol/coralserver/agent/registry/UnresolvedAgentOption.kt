import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.coralprotocol.coralserver.agent.registry.AgentOption
import org.coralprotocol.coralserver.agent.registry.AgentOptionType

@Serializable
data class UnresolvedAgentOption(
    val type: AgentOptionType,
    val description: String? = null,
    val default: AgentOptionDefault? = null
) {
    fun resolve(): AgentOption =
        when (type) {
            AgentOptionType.STRING -> AgentOption.String(description,
                when (default) {
                    is AgentOptionDefault.Number -> throw IllegalArgumentException("Cannot use number as default for string option")
                    is AgentOptionDefault.String -> default.value
                    null -> null
                }
            )
            AgentOptionType.NUMBER -> AgentOption.Number(description,
                when (default) {
                    is AgentOptionDefault.Number -> default.value
                    is AgentOptionDefault.String -> throw IllegalArgumentException("Cannot use string as default for number option")
                    null -> null
                })
            AgentOptionType.SECRET -> AgentOption.Secret(description)
        }
}

@Serializable(with = AgentOptionDefaultSerializer::class)
sealed class AgentOptionDefault() {
    @Serializable
    data class String(val value: kotlin.String) : AgentOptionDefault()

    @Serializable
    data class Number(val value: Double) : AgentOptionDefault()
}

object AgentOptionDefaultSerializer : KSerializer<AgentOptionDefault> {
    override val descriptor = buildClassSerialDescriptor("AgentOptionDefault")
    override fun deserialize(decoder: Decoder): AgentOptionDefault {
        try {
            return AgentOptionDefault.String(decoder.decodeString())
        }
        catch (_: Exception) {

        }

        try {
            return AgentOptionDefault.Number(decoder.decodeDouble())
        }
        catch (_: Exception) {

        }

        throw IllegalArgumentException("Unsupported option format")
    }

    override fun serialize(encoder: Encoder, value: AgentOptionDefault) {
        when (value) {
            is AgentOptionDefault.String -> encoder.encodeString(value.value)
            is AgentOptionDefault.Number -> encoder.encodeDouble(value.value)
        }
    }
}