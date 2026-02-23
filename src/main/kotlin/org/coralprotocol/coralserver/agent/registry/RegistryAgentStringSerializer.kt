@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : PotentialStringReference

    @Serializable
    @SerialName("file")
    data class File(
        val path: kotlin.String,
        val encoding: kotlin.String = "UTF-8"
    ) : PotentialStringReference

    @Serializable
    @SerialName("url")
    data class Url(
        val url: kotlin.String,
        val encoding: kotlin.String = "UTF-8"
    ) : PotentialStringReference
}

class RegistryAgentStringSerializer : KSerializer<String>, KoinComponent {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("String", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val context = registryAgentSerializationContext.get()
            ?: return decoder.decodeString()

        return try {
            when (val reference = decoder.decodeSerializableValue(PotentialStringReference.serializer())) {
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
        } catch (_: IllegalArgumentException) {
            decoder.decodeString()
        }
    }
}