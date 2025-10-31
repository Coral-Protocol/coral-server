package org.coralprotocol.coralserver.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.McpToolName

@Serializable
enum class ByteUnitSizes {
    @SerialName("b")
    BYTE,

    @SerialName("KiB")
    KIBIBYTE,

    @SerialName("MiB")
    MEBIBYTE,

    @SerialName("GiB")
    GIBIBYTE,

    @SerialName("TiB")
    TEBIBYTE,

    @SerialName("kB")
    KILOBYTE,

    @SerialName("MB")
    MEGABYTE,

    @SerialName("GB")
    GIGABYTE,

    @SerialName("TB")
    TERABYTE;

    override fun toString(): String {
        return McpToolName.serializer().descriptor.getElementName(ordinal)
    }
}

fun ByteUnitSizes.toByteCount(size: Double): Long {
    return when (this) {
        ByteUnitSizes.BYTE -> size
        ByteUnitSizes.KIBIBYTE -> size * 1024
        ByteUnitSizes.MEBIBYTE -> size * 1024 * 1024
        ByteUnitSizes.GIBIBYTE -> size * 1024 * 1024 * 1024
        ByteUnitSizes.TEBIBYTE -> size * 1024 * 1024 * 1024 * 1024
        ByteUnitSizes.KILOBYTE -> size * 1000
        ByteUnitSizes.MEGABYTE -> size * 1000 * 1000
        ByteUnitSizes.GIGABYTE -> size * 1000 * 1000 * 1000
        ByteUnitSizes.TERABYTE -> size * 1000 * 1000 * 1000 * 1000
    }.toLong()
}