@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import org.koin.core.component.KoinComponent
import java.io.File
import java.nio.charset.Charset

@Serializable
@JsonClassDiscriminator("type")
sealed interface PotentialStringReference {
    val base64: Boolean?

    @Serializable
    @SerialName("string")
    data class String(
        val value: kotlin.String,
        override val base64: Boolean? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("file")
    data class File(
        val path: kotlin.String,
        val encoding: kotlin.String = "UTF-8",
        override val base64: Boolean? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("url")
    data class Url(
        val url: kotlin.String,
        val encoding: kotlin.String = "UTF-8",
        override val base64: Boolean? = null
    ) : PotentialStringReference
}

open class RegistryAgentStringSerializer : KSerializer<String>, KoinComponent {
    open val base64Default: Boolean = false

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("String", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val context = registryAgentSerializationContext.get()
            ?: return decoder.decodeString()

        return try {
            val reference = decoder.decodeSerializableValue(PotentialStringReference.serializer())
            val text = when (reference) {
                is PotentialStringReference.File -> {
                    if (!context.enableFileReferences)
                        throw IllegalStateException("File references are not enabled")

                    val file = File(reference.path)
                    if (file.isAbsolute || context.agentFilePath == null) {
                        file.readText(Charset.forName(reference.encoding))
                    } else {
                        context.agentFilePath.toFile().resolve(file).readText(Charset.forName(reference.encoding))
                    }
                }

                is PotentialStringReference.String -> reference.value
                is PotentialStringReference.Url -> {
                    if (!context.enableUrlReferences)
                        throw IllegalStateException("Url references are not enabled")

                    runBlocking {
                        context.httpClient.get(reference.url).bodyAsText(Charset.forName(reference.encoding))
                    }
                }
            }

            val base64 = reference.base64 ?: base64Default
            if (base64) {
                text.encodeBase64()
            } else {
                text
            }
        } catch (_: IllegalArgumentException) {
            decoder.decodeString()
        }
    }

}

class RegistryAgentBase64StringSerializer : RegistryAgentStringSerializer() {
    override val base64Default: Boolean
        get() = true
}

object RegistryAgentStringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentStringSerializer())

object RegistryAgentBase64StringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentBase64StringSerializer())