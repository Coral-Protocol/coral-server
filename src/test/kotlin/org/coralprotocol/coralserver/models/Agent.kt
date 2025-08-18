import com.akuleshov7.ktoml.Toml
import io.ktor.server.html.insert
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection

@Serializable
enum class Enum {
    @Serializable
    A,
    @Serializable
    B,
    @Serializable
    C
}

@Serializable(with = EnumsSerializer::class)
data class Enums(
    val enums: List<Enum>
)

object EnumsSerializer: KSerializer<Enums> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Enums",
            serialDescriptor<List<Enum>>())

    override fun serialize(encoder: Encoder, value: Enums) {
        //encoder.encodeCollection(descriptor, value.enums.size) {}
        TODO("Not yet implemented")
    }

    override fun deserialize(decoder: Decoder): Enums {
        val enums = ArrayList<Enum>()
        val struct = decoder.beginStructure(descriptor)
        while (struct.decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
           enums.add(Enum.valueOf(struct.decodeStringElement(descriptor, 0)))
        }
        TODO("Not yet implemented")
    }
}

fun main() {
   // val enums = Enums(listOf(Enum.A, Enum.B, Enum.C))

    val toml = Toml()
    val encoded = "enums = [\"A\"]"// toml.encodeToString(enums)
    println(encoded)
    println(toml.decodeFromString<Enums>(encoded))
}