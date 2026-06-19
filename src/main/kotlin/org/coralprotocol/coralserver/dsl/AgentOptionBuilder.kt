package org.coralprotocol.coralserver.dsl

import me.saket.bytesize.ByteSize
import org.coralprotocol.coralserver.agent.registry.option.*

@CoralDsl
abstract class AgentOptionBuilder<T : PolymorphicAgentOption<*>> {
    var required: Boolean = false
    var transport: AgentOptionTransport = AgentOptionTransport.ENVIRONMENT_VARIABLE
    var label: String? = null
    var description: String? = null
    var group: String? = null
    var multiline: Boolean = false

    protected fun createDisplay(): AgentOptionDisplay? {
        return if (label != null || description != null || group != null || multiline) {
            AgentOptionDisplay(
                label = label,
                description = description,
                group = group,
                multiline = multiline
            )
        } else null
    }

    abstract fun build(): T
}

@CoralDsl
class StringAgentOptionBuilder : AgentOptionBuilder<PolymorphicAgentOption.String>() {
    var default: String? = null
    var base64: Boolean = false
    var secret: Boolean = false
    var variants: List<String>? = null
    var minLength: Int? = null
    var maxLength: Int? = null
    var regex: String? = null

    override fun build(): PolymorphicAgentOption.String {
        val validation = if (variants != null || minLength != null || maxLength != null || regex != null) {
            StringAgentOptionValidation(variants, minLength, maxLength, regex)
        } else null
        return PolymorphicAgentOption.String(default, validation, base64, secret, required, createDisplay(), transport)
    }
}

@CoralDsl
class StringListAgentOptionBuilder : AgentOptionBuilder<PolymorphicAgentOption.StringList>() {
    var default: List<String> = listOf()
    var base64: Boolean = false
    var secret: Boolean = false
    var variants: List<String>? = null
    var minLength: Int? = null
    var maxLength: Int? = null
    var regex: String? = null

    override fun build(): PolymorphicAgentOption.StringList {
        val validation = if (variants != null || minLength != null || maxLength != null || regex != null) {
            StringAgentOptionValidation(variants, minLength, maxLength, regex)
        } else null
        return PolymorphicAgentOption.StringList(
            default,
            validation,
            base64,
            secret,
            required,
            createDisplay(),
            transport
        )
    }
}

@CoralDsl
class BlobAgentOptionBuilder : AgentOptionBuilder<PolymorphicAgentOption.Blob>() {
    var default: String? = null
    var minSize: ByteSize? = null
    var maxSize: ByteSize? = null

    override fun build(): PolymorphicAgentOption.Blob {
        val validation = if (minSize != null || maxSize != null) {
            BlobAgentOptionValidation(minSize, maxSize)
        } else null
        return PolymorphicAgentOption.Blob(default, validation, required, createDisplay(), transport)
    }
}

@CoralDsl
class BlobListAgentOptionBuilder : AgentOptionBuilder<PolymorphicAgentOption.BlobList>() {
    var default: List<String> = listOf()
    var minSize: ByteSize? = null
    var maxSize: ByteSize? = null

    override fun build(): PolymorphicAgentOption.BlobList {
        val validation = if (minSize != null || maxSize != null) {
            BlobAgentOptionValidation(minSize, maxSize)
        } else null
        return PolymorphicAgentOption.BlobList(default, validation, required, createDisplay(), transport)
    }
}

@CoralDsl
class BooleanAgentOptionBuilder : AgentOptionBuilder<PolymorphicAgentOption.Boolean>() {
    var default: Boolean? = null

    override fun build(): PolymorphicAgentOption.Boolean {
        return PolymorphicAgentOption.Boolean(default, required, createDisplay(), transport)
    }
}

@CoralDsl
abstract class NumericAgentOptionBuilder<Value : Comparable<Value>, Option : PolymorphicAgentOption<*>, Validation : NumericAgentOptionValidation<Value>> :
    AgentOptionBuilder<Option>() {
    var default: Value? = null
    var variants: List<Value>? = null
    var min: Value? = null
    var max: Value? = null

    abstract fun createValidation(variants: List<Value>?, min: Value?, max: Value?): Validation
    abstract fun createOption(
        default: Value?,
        validation: Validation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ): Option

    override fun build(): Option {
        val validation = if (variants != null || min != null || max != null) {
            createValidation(variants, min, max)
        } else null
        return createOption(default, validation, required, createDisplay(), transport)
    }
}

@CoralDsl
abstract class NumericListAgentOptionBuilder<ValueType : Comparable<ValueType>, OptionType : PolymorphicAgentOption<*>, Validation : NumericAgentOptionValidation<ValueType>> :
    AgentOptionBuilder<OptionType>() {
    var default: List<ValueType> = listOf()
    var variants: List<ValueType>? = null
    var min: ValueType? = null
    var max: ValueType? = null

    abstract fun createValidation(variants: List<ValueType>?, min: ValueType?, max: ValueType?): Validation
    abstract fun createOption(
        default: List<ValueType>,
        validation: Validation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ): OptionType

    override fun build(): OptionType {
        val validation = if (variants != null || min != null || max != null) {
            createValidation(variants, min, max)
        } else null
        return createOption(default, validation, required, createDisplay(), transport)
    }
}

@CoralDsl
class ByteAgentOptionBuilder :
    NumericAgentOptionBuilder<Byte, PolymorphicAgentOption.Byte, ByteAgentOptionValidation>() {
    override fun createValidation(variants: List<Byte>?, min: Byte?, max: Byte?) =
        ByteAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Byte?,
        validation: ByteAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Byte(default, validation, required, display, transport)
}

@CoralDsl
class ByteListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Byte, PolymorphicAgentOption.ByteList, ByteAgentOptionValidation>() {
    override fun createValidation(variants: List<Byte>?, min: Byte?, max: Byte?) =
        ByteAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Byte>,
        validation: ByteAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.ByteList(default, validation, required, display, transport)
}

@CoralDsl
class ShortAgentOptionBuilder :
    NumericAgentOptionBuilder<Short, PolymorphicAgentOption.Short, ShortAgentOptionValidation>() {
    override fun createValidation(variants: List<Short>?, min: Short?, max: Short?) =
        ShortAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Short?,
        validation: ShortAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Short(default, validation, required, display, transport)
}

@CoralDsl
class ShortListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Short, PolymorphicAgentOption.ShortList, ShortAgentOptionValidation>() {
    override fun createValidation(variants: List<Short>?, min: Short?, max: Short?) =
        ShortAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Short>,
        validation: ShortAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.ShortList(default, validation, required, display, transport)
}

@CoralDsl
class IntAgentOptionBuilder :
    NumericAgentOptionBuilder<Int, PolymorphicAgentOption.Int, IntAgentOptionValidation>() {
    override fun createValidation(variants: List<Int>?, min: Int?, max: Int?) =
        IntAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Int?,
        validation: IntAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Int(default, validation, required, display, transport)
}

@CoralDsl
class IntListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Int, PolymorphicAgentOption.IntList, IntAgentOptionValidation>() {
    override fun createValidation(variants: List<Int>?, min: Int?, max: Int?) =
        IntAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Int>,
        validation: IntAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.IntList(default, validation, required, display, transport)
}

@CoralDsl
class LongAgentOptionBuilder :
    NumericAgentOptionBuilder<Long, PolymorphicAgentOption.Long, LongAgentOptionValidation>() {
    override fun createValidation(variants: List<Long>?, min: Long?, max: Long?) =
        LongAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Long?,
        validation: LongAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Long(default, validation, required, display, transport)
}

@CoralDsl
class LongListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Long, PolymorphicAgentOption.LongList, LongAgentOptionValidation>() {
    override fun createValidation(variants: List<Long>?, min: Long?, max: Long?) =
        LongAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Long>,
        validation: LongAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.LongList(default, validation, required, display, transport)
}

@CoralDsl
class UByteAgentOptionBuilder :
    NumericAgentOptionBuilder<UByte, PolymorphicAgentOption.UByte, UByteAgentOptionValidation>() {
    override fun createValidation(variants: List<UByte>?, min: UByte?, max: UByte?) =
        UByteAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: UByte?,
        validation: UByteAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UByte(default, validation, required, display, transport)
}

@CoralDsl
class UByteListAgentOptionBuilder :
    NumericListAgentOptionBuilder<UByte, PolymorphicAgentOption.UByteList, UByteAgentOptionValidation>() {
    override fun createValidation(variants: List<UByte>?, min: UByte?, max: UByte?) =
        UByteAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<UByte>,
        validation: UByteAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UByteList(default, validation, required, display, transport)
}

@CoralDsl
class UShortAgentOptionBuilder :
    NumericAgentOptionBuilder<UShort, PolymorphicAgentOption.UShort, UShortAgentOptionValidation>() {
    override fun createValidation(variants: List<UShort>?, min: UShort?, max: UShort?) =
        UShortAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: UShort?,
        validation: UShortAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UShort(default, validation, required, display, transport)
}

@CoralDsl
class UShortListAgentOptionBuilder :
    NumericListAgentOptionBuilder<UShort, PolymorphicAgentOption.UShortList, UShortAgentOptionValidation>() {
    override fun createValidation(variants: List<UShort>?, min: UShort?, max: UShort?) =
        UShortAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<UShort>,
        validation: UShortAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UShortList(default, validation, required, display, transport)
}

@CoralDsl
class UIntAgentOptionBuilder :
    NumericAgentOptionBuilder<UInt, PolymorphicAgentOption.UInt, UIntAgentOptionValidation>() {
    override fun createValidation(variants: List<UInt>?, min: UInt?, max: UInt?) =
        UIntAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: UInt?,
        validation: UIntAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UInt(default, validation, required, display, transport)
}

@CoralDsl
class UIntListAgentOptionBuilder :
    NumericListAgentOptionBuilder<UInt, PolymorphicAgentOption.UIntList, UIntAgentOptionValidation>() {
    override fun createValidation(variants: List<UInt>?, min: UInt?, max: UInt?) =
        UIntAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<UInt>,
        validation: UIntAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.UIntList(default, validation, required, display, transport)
}

@CoralDsl
class ULongAgentOptionBuilder :
    NumericAgentOptionBuilder<ULong, PolymorphicAgentOption.ULong, ULongAgentOptionValidation>() {
    var defaultString: String? = null
    override fun createValidation(variants: List<ULong>?, min: ULong?, max: ULong?) =
        ULongAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: ULong?,
        validation: ULongAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.ULong(defaultString ?: default?.toString(), validation, required, display, transport)
}

@CoralDsl
class ULongListAgentOptionBuilder :
    NumericListAgentOptionBuilder<ULong, PolymorphicAgentOption.ULongList, ULongAgentOptionValidation>() {
    var defaultStrings: List<String> = listOf()
    override fun createValidation(variants: List<ULong>?, min: ULong?, max: ULong?) =
        ULongAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<ULong>,
        validation: ULongAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.ULongList(
        defaultStrings.ifEmpty { default.map { it.toString() } },
        validation,
        required,
        display,
        transport
    )
}

@CoralDsl
class FloatAgentOptionBuilder :
    NumericAgentOptionBuilder<Float, PolymorphicAgentOption.Float, FloatAgentOptionValidation>() {
    override fun createValidation(variants: List<Float>?, min: Float?, max: Float?) =
        FloatAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Float?,
        validation: FloatAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Float(default, validation, required, display, transport)
}

@CoralDsl
class FloatListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Float, PolymorphicAgentOption.FloatList, FloatAgentOptionValidation>() {
    override fun createValidation(variants: List<Float>?, min: Float?, max: Float?) =
        FloatAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Float>,
        validation: FloatAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.FloatList(default, validation, required, display, transport)
}

@CoralDsl
class DoubleAgentOptionBuilder :
    NumericAgentOptionBuilder<Double, PolymorphicAgentOption.Double, DoubleAgentOptionValidation>() {
    override fun createValidation(variants: List<Double>?, min: Double?, max: Double?) =
        DoubleAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: Double?,
        validation: DoubleAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.Double(default, validation, required, display, transport)
}

@CoralDsl
class DoubleListAgentOptionBuilder :
    NumericListAgentOptionBuilder<Double, PolymorphicAgentOption.DoubleList, DoubleAgentOptionValidation>() {
    override fun createValidation(variants: List<Double>?, min: Double?, max: Double?) =
        DoubleAgentOptionValidation(variants, min, max)

    override fun createOption(
        default: List<Double>,
        validation: DoubleAgentOptionValidation?,
        required: Boolean,
        display: AgentOptionDisplay?,
        transport: AgentOptionTransport
    ) = PolymorphicAgentOption.DoubleList(default, validation, required, display, transport)
}
