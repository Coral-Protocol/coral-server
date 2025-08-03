@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder

//@Polymorphic
@Serializable(with = Serializer::class)
@JsonClassDiscriminator("also_cheese")
sealed interface OneOrMany<out T> {
    @Serializable
    data class Single<T>(val value: T) : OneOrMany<T>

    @Serializable
    data class Many<T>(val values: List<T>) : OneOrMany<T>

    companion object {
        fun <T> of(value: T): OneOrMany<T> = Single(value)
        fun <T> of(values: List<T>): OneOrMany<T> = when {
            values.isEmpty() -> throw IllegalArgumentException("List must not be empty")
            values.size == 1 -> Single(values.first())
            else -> Many(values)
        }
    }
}

@Suppress("unused")
fun <T> OneOrMany<T>.toList(): List<T> = when (this) {
    is OneOrMany.Single -> listOf(value)
    is OneOrMany.Many -> values
}

fun <T> OneOrMany<T>.first(): T = when (this) {
    is OneOrMany.Single -> value
    is OneOrMany.Many -> values.first()
}

fun <T> OneOrMany<T>.size(): Int = when (this) {
    is OneOrMany.Single -> 1
    is OneOrMany.Many -> values.size
}

class Serializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<OneOrMany<T>> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("OneOrMany", PolymorphicKind.SEALED) {
        element("Single", dataSerializer.descriptor)
        element("Many", ListSerializer(dataSerializer).descriptor)
    }


    override fun serialize(encoder: Encoder, value: OneOrMany<T>) {
        when (value) {
            is OneOrMany.Single -> encoder.encodeSerializableValue(dataSerializer, value.value)
            is OneOrMany.Many -> encoder.encodeSerializableValue(ListSerializer(dataSerializer), value.values)
        }
    }

    override fun deserialize(decoder: Decoder): OneOrMany<T> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("OneOrMany can only be deserialized from JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonArray -> {
                val values = jsonDecoder.json.decodeFromJsonElement(
                    ListSerializer(dataSerializer),
                    element
                )
                OneOrMany.Many(values)
            }
            else -> {
                val value = jsonDecoder.json.decodeFromJsonElement(dataSerializer, element)
                OneOrMany.Single(value)
            }
        }
    }
}
