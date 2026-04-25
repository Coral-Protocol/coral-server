@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.llmproxy

import dev.eav.tomlkt.TomlClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.llmproxy.strategies.AnthropicStrategy
import org.coralprotocol.coralserver.llmproxy.strategies.OpenAIStrategy

/**
 * WARNING!
 *
 * This class is deserialized via Hoplite as well as by kotlinx (JSON & TOML).  Hoplite's polymorphic deserialization
 * uses the class name, there is no way to change this.  `.withResolveTypesCaseInsensitive()` appears to be broken for
 * this too.
 *
 * To maintain compatibility between all required encoders/decoders, this class, unlike every other class, must have
 * serial names that match class names.
 */

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface LlmProviderFormat : LlmProviderStrategy {
    @Serializable
    @SerialName("OpenAI")
    object OpenAI : LlmProviderFormat, LlmProviderStrategy by OpenAIStrategy {
        override fun toString(): String {
            return serializer().descriptor.serialName
        }
    }

    @Serializable
    @SerialName("Anthropic")
    object Anthropic : LlmProviderFormat, LlmProviderStrategy by AnthropicStrategy {
        override fun toString(): String {
            return OpenAI.serializer().descriptor.serialName
        }
    }
}