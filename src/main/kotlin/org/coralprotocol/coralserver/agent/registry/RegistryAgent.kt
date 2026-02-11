@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.saket.bytesize.BinaryByteSize
import me.saket.bytesize.kibibytes
import me.saket.bytesize.mebibytes
import org.bitcoinj.core.AddressFormatException
import org.bitcoinj.core.Base58
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.defaultAsValue
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path

/**
 * If this version of the server supports earlier versions of agent definitions, this field specifies the lowest.
 */
const val MINIMUM_SUPPORTED_AGENT_EDITION = 3

/**
 * The maximum (and current) supported agent edition.
 */
const val MAXIMUM_SUPPORTED_AGENT_VERSION = 3

// [agent]
val AGENT_NAME_LENGTH = 1..32
val AGENT_NAME_PATTERN = "^[a-z0-9]([a-z0-9]*(-[a-z0-9]+)*)?$".toRegex()
val AGENT_VERSION_LENGTH = 1..24

// [runtimes.docker]
val AGENT_DOCKER_IMAGE_LENGTH = 1..512
val AGENT_DOCKER_COMMAND_ENTRIES = 0..1024
val AGENT_DOCKER_COMMAND_MAX_SIZE = 2.kibibytes

// [runtimes.executable]
val AGENT_EXECUTABLE_PATH_LENGTH = 1..4096
val AGENT_EXECUTABLE_ARGUMENTS_ENTRIES = 0..1024
val AGENT_EXECUTABLE_ARGUMENTS_SIZE = 2.kibibytes

// [options]
const val AGENT_OPTION_MAX_ENTRIES = 512
val AGENT_OPTION_NAME_LENGTH = 1..256
val AGENT_OPTION_NAME_PATTERN = "^[a-zA-Z_][a-zA-Z_$0-9]*$".toRegex()
val AGENT_OPTION_DEFAULTS_MAX_SIZE = 6.mebibytes
val AGENT_OPTION_DISPLAY_LABEL_LENGTH = 1..64
val AGENT_OPTION_DISPLAY_DESCRIPTION_LENGTH = 1..1024
val AGENT_OPTION_DISPLAY_GROUP_LENGTH = 1..64

// [marketplace]
val AGENT_MARKETPLACE_SUMMARY_LENGTH = 1..256
val AGENT_MARKETPLACE_README_MAX_SIZE = 1..4096
val AGENT_MARKETPLACE_LICENSE_LENGTH = 1..64

// [marketplace.links]
const val AGENT_MARKETPLACE_LINKS_MAX_ENTRIES = 16
val AGENT_MARKETPLACE_LINKS_NAME_LENGTH = 1..32
val AGENT_MARKETPLACE_LINKS_NAME_PATTERN = "^[a-zA-Z][a-zA-Z_\\-0-9]*$".toRegex()
val AGENT_MARKETPLACE_LINK_VALUE_LENGTH = 1..256

// [marketplace.pricing]
val AGENT_MARKETPLACE_PRICING_DESCRIPTION_LENGTH = 1..256
const val AGENT_MARKETPLACE_PRICING_MIN_MIN = 0.00
const val AGENT_MARKETPLACE_PRICING_MIN_MAX = 20.00

// [marketplace.identities.erc8004]
const val AGENT_MARKETPLACE_ERC8004_ENDPOINTS_MAX_ENTRIES = 32
val AGENT_MARKETPLACE_ERC8004_ENDPOINTS_NAME_LENGTH = 1..32
val AGENT_MARKETPLACE_ERC8004_ENDPOINTS_NAME_PATTERN = "^[a-zA-Z][a-zA-Z_\\-0-9]*$".toRegex()
val AGENT_MARKETPLACE_ERC8004_ENDPOINTS_ENDPOINT_LENGTH = 1..256

@Serializable
data class RegistryAgent(
    private val info: RegistryAgentInfo,
    val edition: Int = MAXIMUM_SUPPORTED_AGENT_VERSION,
    val runtimes: LocalAgentRuntimes,
    val options: Map<String, AgentOption> = mapOf(),
    val marketplace: RegistryAgentMarketplaceSettings? = null,

    @Transient
    val path: Path? = null,

    @Transient
    private val unresolvedExportSettings: Map<RuntimeId, UnresolvedAgentExportSettings> = mapOf(),
) {
    @Transient
    val description = info.description

    @Transient
    val identifier = info.identifier

    @Transient
    val name = identifier.name

    @Transient

    val version = identifier.version

    @Transient
    val capabilities = info.capabilities

    val exportSettings: AgentExportSettingsMap = unresolvedExportSettings.mapValues { (runtime, settings) ->
        settings.resolve(runtime, this)
    }

    @Transient
    val defaultOptions = options
        .mapNotNull { (name, option) -> option.defaultAsValue()?.let { name to it } }
        .toMap()

    @Transient
    val requiredOptions = options
        .filterValues { it.required }

    private fun validateStringLength(name: String, string: String, range: IntRange) {
        if (range.first > 0 && string.isEmpty())
            throw RegistryException("\"$name\" must not be empty")

        if (string.length < range.first)
            throw RegistryException("\"$name\" must be at least ${range.first} characters long, was ${string.length}")

        if (string.length > range.last)
            throw RegistryException("\"$name\" must be at most ${range.last} characters long, was ${string.length}")
    }

    private fun validateStringList(
        name: String,
        list: List<String>,
        listRange: IntRange,
        maxTotalSize: BinaryByteSize
    ) {
        if (listRange.first > 0 && listRange.isEmpty())
            throw RegistryException("\"$name\" must not be empty")

        if (list.size < listRange.first)
            throw RegistryException("\"$name\" must have at least ${listRange.first} entries, has ${list.size}")

        if (list.size > listRange.last)
            throw RegistryException("\"$name\" must have at most ${listRange.last} entries, has ${list.size}")

        val size = BinaryByteSize(list.sumOf { it.toByteArray().size })
        if (size > maxTotalSize)
            throw RegistryException("total size for \"$name\" must be at most $maxTotalSize, was $size")
    }

    private fun validateName() {
        validateStringLength("agent.name", name, AGENT_NAME_LENGTH)

        if (!name.matches(AGENT_NAME_PATTERN))
            throw RegistryException("value for \"agent.name\" ($name) must start with a lowercase alphabetic character and contain only lowercase alphanumeric characters or '-'")
    }

    private fun validateVersion() {
        validateStringLength("agent.version", version, AGENT_VERSION_LENGTH)

        try {
            Version.parse(version)
        } catch (e: VersionFormatException) {
            throw RegistryException("invalid version provided for \"agent.version\": ${e.message}")
        }
    }

    private fun validateRuntimes() {
        if (runtimes.functionRuntime == null && runtimes.dockerRuntime == null && runtimes.executableRuntime == null)
            throw RegistryException("Must have at least one defined runtime")

        val docker = runtimes.dockerRuntime
        if (docker != null) {
            validateStringLength("runtimes.docker.image", docker.image, AGENT_DOCKER_IMAGE_LENGTH)

            if (docker.command != null) {
                validateStringList(
                    "runtimes.docker.command",
                    docker.command,
                    AGENT_DOCKER_COMMAND_ENTRIES,
                    AGENT_DOCKER_COMMAND_MAX_SIZE
                )
            }
        }

        val executable = runtimes.executableRuntime
        if (executable != null) {
            validateStringLength("runtimes.executable.path", executable.path, AGENT_EXECUTABLE_PATH_LENGTH)

            validateStringList(
                "runtimes.executable.arguments",
                executable.arguments,
                AGENT_EXECUTABLE_ARGUMENTS_ENTRIES,
                AGENT_EXECUTABLE_ARGUMENTS_SIZE
            )
        }
    }

    private fun validateOptions() {
        if (options.size > AGENT_OPTION_MAX_ENTRIES)
            throw RegistryException("option count cannot exceed $AGENT_OPTION_MAX_ENTRIES, found ${options.size} defined options")

        var accumulatedDefaultSize = BinaryByteSize(0)
        for ((name, option) in options) {
            validateStringLength("options.$name", name, AGENT_OPTION_NAME_LENGTH)

            if (!name.matches(AGENT_OPTION_NAME_PATTERN))
                throw RegistryException("option name \"$name\" is not valid.  Option names must start with an alphabetic character or underscore and contain only alphanumeric characters or underscores")

            val label = option.display?.label
            if (label != null)
                validateStringLength("options.$name.display.label", label, AGENT_OPTION_DISPLAY_LABEL_LENGTH)

            val description = option.display?.description
            if (description != null) {
                validateStringLength(
                    "options.$name.display.description",
                    description,
                    AGENT_OPTION_DISPLAY_DESCRIPTION_LENGTH
                )
            }

            val group = option.display?.group
            if (group != null)
                validateStringLength("options.$name.display.group", group, AGENT_OPTION_DISPLAY_GROUP_LENGTH)

            accumulatedDefaultSize += BinaryByteSize(
                when (option) {
                    is AgentOption.Blob -> option.defaultBytes?.size ?: 0
                    is AgentOption.BlobList -> option.defaultBytes.sumOf { it.size }
                    is AgentOption.Boolean -> option.default?.let { 1 } ?: 0
                    is AgentOption.Byte -> option.default?.let { Byte.SIZE_BYTES } ?: 0
                    is AgentOption.ByteList -> option.default.size
                    is AgentOption.Double -> option.default?.let { Double.SIZE_BYTES } ?: 0
                    is AgentOption.DoubleList -> option.default.size * Double.SIZE_BYTES
                    is AgentOption.Float -> option.default?.let { Float.SIZE_BYTES } ?: 0
                    is AgentOption.FloatList -> option.default.size * Float.SIZE_BYTES
                    is AgentOption.Int -> option.default?.let { Int.SIZE_BYTES } ?: 0
                    is AgentOption.IntList -> option.default.size * Int.SIZE_BYTES
                    is AgentOption.Long -> option.default?.let { Long.SIZE_BYTES } ?: 0
                    is AgentOption.LongList -> option.default.size * Long.SIZE_BYTES
                    is AgentOption.Short -> option.default?.let { Short.SIZE_BYTES } ?: 0
                    is AgentOption.ShortList -> option.default.size * Short.SIZE_BYTES
                    is AgentOption.String -> option.default?.toByteArray()?.size ?: 0
                    is AgentOption.StringList -> option.default.sumOf { it.toByteArray().size }
                    is AgentOption.UByte -> option.default?.let { UByte.SIZE_BYTES } ?: 0
                    is AgentOption.UByteList -> option.default.size * UByte.SIZE_BYTES
                    is AgentOption.UInt -> option.default?.let { UInt.SIZE_BYTES } ?: 0
                    is AgentOption.UIntList -> option.default.size * UInt.SIZE_BYTES
                    is AgentOption.ULong -> option.default?.toByteArray()?.size ?: 0
                    is AgentOption.ULongList -> option.default.sumOf { it.toByteArray().size }
                    is AgentOption.UShort -> option.default?.let { UShort.SIZE_BYTES } ?: 0
                    is AgentOption.UShortList -> option.default.size * UShort.SIZE_BYTES
                })
        }

        if (accumulatedDefaultSize > AGENT_OPTION_DEFAULTS_MAX_SIZE)
            throw RegistryException("total size for all default values cannot exceed $AGENT_OPTION_DEFAULTS_MAX_SIZE, was $accumulatedDefaultSize")
    }

    private fun validateMarketplace() {
        if (marketplace == null)
            return

        validateStringLength("marketplace.summary", marketplace.summary, AGENT_MARKETPLACE_SUMMARY_LENGTH)
        validateStringLength("marketplace.readme", marketplace.readme, AGENT_MARKETPLACE_README_MAX_SIZE)

        if (marketplace.license != null)
            validateStringLength("marketplace.license", marketplace.license, AGENT_MARKETPLACE_LICENSE_LENGTH)

        if (marketplace.links.size > AGENT_MARKETPLACE_LINKS_MAX_ENTRIES)
            throw RegistryException("marketplace link count cannot exceed $AGENT_MARKETPLACE_LINKS_MAX_ENTRIES, was ${marketplace.links.size}")

        for ((name, link) in marketplace.links) {
            validateStringLength("marketplace.links[\"$name\"] (key)", name, AGENT_MARKETPLACE_LINKS_NAME_LENGTH)

            if (!name.matches(AGENT_MARKETPLACE_LINKS_NAME_PATTERN))
                throw RegistryException("marketplace link \"$name\" is not valid.  Marketplace link names must start with an alphabetic character and contain only alphanumeric characters or underscores")

            try {
                validateStringLength("marketplace.links[\"$name\"] (value)", link, AGENT_MARKETPLACE_LINK_VALUE_LENGTH)
                val uri = URI(link)
                if (uri.scheme != "https" && uri.scheme != "mailto" && uri.scheme != "tel")
                    throw RegistryException("marketplace link \"$name\" must use a HTTPS, mailto, or tel scheme")
            } catch (e: URISyntaxException) {
                throw RegistryException("marketplace link \"$name\" is not a valid URL: ${e.message}")
            }
        }

        val pricing = marketplace.pricing
        if (pricing != null) {
            validateStringLength(
                "marketplace.pricing.description",
                pricing.description,
                AGENT_MARKETPLACE_PRICING_DESCRIPTION_LENGTH
            )

            if (pricing.currency != "USD")
                throw RegistryException("marketplace pricing currency must be USD")

            if (pricing.recommendations.min < AGENT_MARKETPLACE_PRICING_MIN_MIN)
                throw RegistryException("marketplace pricing minimum recommendation must be at least $AGENT_MARKETPLACE_PRICING_MIN_MIN")

            if (pricing.recommendations.min > AGENT_MARKETPLACE_PRICING_MIN_MAX)
                throw RegistryException("marketplace pricing minimum recommendation must be at most $AGENT_MARKETPLACE_PRICING_MIN_MAX")

            if (pricing.recommendations.max <= pricing.recommendations.min)
                throw RegistryException("marketplace pricing maximum recommendation must be greater than minimum recommendation")
        }

        val erc8004 = marketplace.identities?.erc8004
        if (erc8004 != null) {
            try {
                val bytes = Base58.decode(erc8004.wallet)
                if (bytes.size !in 25..32)
                    throw RegistryException("marketplace.identities.erc8004.wallet must be between 25 and 32 bytes long, was ${bytes.size}")
            } catch (e: AddressFormatException) {
                throw RegistryException("marketplace.identities.erc8004.wallet is not a valid Base58-encoded wallet address: ${e.message}")
            }

            if (erc8004.endpoints.size > AGENT_MARKETPLACE_ERC8004_ENDPOINTS_MAX_ENTRIES)
                throw RegistryException("marketplace.identities.erc8004.endpoints cannot exceed $AGENT_MARKETPLACE_ERC8004_ENDPOINTS_MAX_ENTRIES, found ${erc8004.endpoints.size} defined")

            for ((index, endpoint) in erc8004.endpoints.withIndex()) {
                validateStringLength(
                    "marketplace.identities.erc8004.endpoints[$index].name",
                    endpoint.name,
                    AGENT_MARKETPLACE_ERC8004_ENDPOINTS_NAME_LENGTH
                )

                if (!endpoint.name.matches(AGENT_MARKETPLACE_ERC8004_ENDPOINTS_NAME_PATTERN))
                    throw RegistryException("marketplace.identities.erc8004.endpoints[$index].name is not valid.  Marketplace endpoint names must start with an alphabetic character and contain only alphanumeric characters or, underscores or '-'s ")

                validateStringLength(
                    "marketplace.identities.erc8004.endpoints[$index].endpoint",
                    endpoint.endpoint,
                    AGENT_MARKETPLACE_ERC8004_ENDPOINTS_ENDPOINT_LENGTH
                )

                try {
                    val uri = URI(endpoint.endpoint)
                    if (uri.scheme != "https")
                        throw RegistryException("marketplace.identities.erc8004.endpoints[$index].endpoint must use a HTTPS scheme")
                } catch (e: URISyntaxException) {
                    throw RegistryException("marketplace.identities.erc8004.endpoints[$index].endpoint is not a valid URL: ${e.message}")
                }
            }
        }
    }

    /**
     * Validates values in this registry agent to ensure they are compliant with the requirements for the marketplace.
     *
     * @throws RegistryException if this registry agent contains any number of invalid values
     */
    fun validate() {
        validateName()
        validateVersion()
        validateRuntimes()
        validateOptions()
        validateMarketplace()
    }
}

@Serializable
data class PublicRegistryAgent(
    val id: RegistryAgentIdentifier,
    val runtimes: List<RuntimeId>,
    val options: Map<String, AgentOption>,
    val exportSettings: PublicAgentExportSettingsMap
)

fun RegistryAgent.toPublic(): PublicRegistryAgent = PublicRegistryAgent(
    id = identifier,
    runtimes = runtimes.toRuntimeIds(),
    options = options,
    exportSettings = exportSettings.mapValues { (_, settings) -> settings.toPublic() }
)