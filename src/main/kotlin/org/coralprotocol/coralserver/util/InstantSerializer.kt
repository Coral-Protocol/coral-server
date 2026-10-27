@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun utcTimeNow() = Clock.System.now()

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UTC timestamp ISO-8601", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            Instant.parse(text)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("\"$text\" is not an ISO-8601 time, e.g. 2026-10-25T12:30:00.123Z", e)
        }
    }
}
