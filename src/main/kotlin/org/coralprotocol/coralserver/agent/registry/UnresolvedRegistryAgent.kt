package org.coralprotocol.coralserver.agent.registry

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.decodeFromNativeReader
import dev.eav.tomlkt.decodeFromString
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.client.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionSerializerMap
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOption
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import java.nio.file.Path

const val AGENT_FILE = "coral-agent.toml"

data class RegistryAgentSerializationContext(
    val agentFilePath: Path?,
    val httpClient: HttpClient,
    val enableFileReferences: Boolean,
    val enableUrlReferences: Boolean
)

val registryAgentSerializationContext: ThreadLocal<RegistryAgentSerializationContext?> =
    ThreadLocal.withInitial { null }

@Serializable
data class UnresolvedRegistryAgent(
    @Description("The edition of this agent")
    val edition: Int,

    @SerialName("agent")
    val agentInfo: UnresolvedRegistryAgentInfo,

    @Description("The runtimes that this agent supports")
    @Optional
    val runtimes: LocalAgentRuntimes = LocalAgentRuntimes(),

    @Description("The options that this agent supports, for example the API keys required for the agent to function")
    @Optional
    @Serializable(with = AgentOptionSerializerMap::class)
    val options: Map<String, AgentOption> = mapOf(),

    @Description("LLM proxy configuration declaring which proxy endpoints this agent needs")
    @Optional
    val llm: AgentLlmConfig? = null,

    @Description("Information for this agent relevant to it's potential listing on the marketplace")
    @Optional
    val marketplace: RegistryAgentMarketplaceSettings? = null,

    @Description("A list of dependencies, grouping options together. Dependencies are also required by claim types.")
    @Optional
    val dependencies: List<RegistryAgentDependency> = emptyList(),

    @Description("A list of claim types that can be made by this agent during runtime")
    @Optional
    @SerialName("claims")
    val claimTypes: List<RegistryAgentClaimType> = emptyList(),
) : KoinComponent {
    private val logger by inject<Logger>(named(LOGGER_CONFIG))

    companion object : KoinComponent {
        fun resolveFromFile(
            file: File,
            enableFileReferences: Boolean = true,
            enableUrlReferences: Boolean = true
        ): RegistryAgent {
            val path = file.parentFile.toPath()
            registryAgentSerializationContext.set(
                RegistryAgentSerializationContext(
                    path,
                    get(),
                    enableFileReferences,
                    enableUrlReferences
                )
            )

            val agent = get<Toml>().decodeFromNativeReader<UnresolvedRegistryAgent>(file.reader()).resolve(
                AgentResolutionContext(
                    registrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
                    path = path
                )
            )

            registryAgentSerializationContext.remove()

            return agent
        }

        fun resolveFromString(
            string: String,
            enableFileReferences: Boolean = true,
            enableUrlReferences: Boolean = true
        ): RegistryAgent {
            registryAgentSerializationContext.set(
                RegistryAgentSerializationContext(
                    null,
                    get(),
                    enableFileReferences,
                    enableUrlReferences
                )
            )

            val agent = get<Toml>().decodeFromString<UnresolvedRegistryAgent>(string)
                .resolve(AgentResolutionContext(registrySourceIdentifier = AgentRegistrySourceIdentifier.Local))

            registryAgentSerializationContext.remove()

            return agent
        }
    }

    fun resolve(context: AgentResolutionContext): RegistryAgent {
        if (edition < MINIMUM_SUPPORTED_AGENT_EDITION) {
            throw RegistryException("Agent ${context.path} has invalid edition '$edition', must be at least $MINIMUM_SUPPORTED_AGENT_EDITION")
        } else if (edition > MAXIMUM_SUPPORTED_AGENT_VERSION) {
            throw RegistryException("Agent ${context.path} has edition '$edition', this server's highest supported edition is '$MAXIMUM_SUPPORTED_AGENT_VERSION'")
        }

        options.forEach { (name, option) ->
            val locator = "Option '${name} in agent ${context.path}"

            if (option.required && option.default != null)
                logger.warn { "$locator 'required = true' is not needed as the default value is set." }

            if ((option is PolymorphicAgentOption.String && option.base64 || option is PolymorphicAgentOption.StringList && option.base64) && option.transport == AgentOptionTransport.FILE_SYSTEM)
                logger.warn { "$locator has 'base64 = true' and 'transport = 'fs''.  The base64 field will be ignored" }

            // ugly just like the rest of AgentOption.*'s hideous mess of when statements!
            val emptyVariants = when (option) {
                is PolymorphicAgentOption.Byte -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.ByteList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.Double -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.DoubleList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.Float -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.FloatList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.Int -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.IntList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.Long -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.LongList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.Short -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.ShortList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.String -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.StringList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UByte -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UByteList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UInt -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UIntList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.ULong -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.ULongList -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UShort -> option.validation?.variants?.isEmpty() ?: false
                is PolymorphicAgentOption.UShortList -> option.validation?.variants?.isEmpty() ?: false
                else -> {
                    // no variants
                    false
                }
            }

            if (emptyVariants)
                logger.warn { "$locator has an empty variant list, this will match no values!  The variants field will be ignored" }
        }

        val registryAgent = RegistryAgent(
            edition = edition,
            info = agentInfo.resolve(context.registrySourceIdentifier),
            runtimes = runtimes,
            options = options,
            llm = llm,
            path = context.path,
            marketplace = marketplace,
            dependencies = dependencies,
            claimTypes = claimTypes
        )
        registryAgent.validate()

        return registryAgent
    }
}
