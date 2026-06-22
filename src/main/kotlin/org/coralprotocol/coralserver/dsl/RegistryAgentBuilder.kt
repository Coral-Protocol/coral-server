package org.coralprotocol.coralserver.dsl

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOption
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeUrlPart
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.streamableHttpFunctionRuntime
import java.nio.file.Path

@CoralDsl
class PrototypeStringBuilder {
    fun inline(value: String): PrototypeString = PrototypeString.Inline(value)
    fun option(name: String): PrototypeString = PrototypeString.Option(name)
}

@CoralDsl
class PrototypeStringListBuilder {
    private val parts = mutableListOf<PrototypeString>()

    fun inline(value: String) {
        parts += PrototypeString.Inline(value)
    }

    fun option(name: String) {
        parts += PrototypeString.Option(name)
    }

    fun composedString(separator: String = "", block: PrototypeStringListBuilder.() -> Unit) {
        parts += PrototypeString.ComposedString(
            parts = PrototypeStringListBuilder().apply(block).build(),
            separator = separator
        )
    }

    fun composedUrl(base: String, block: UrlPartListBuilder.() -> Unit) {
        parts += PrototypeString.ComposedUrl(
            base = base,
            parts = UrlPartListBuilder().apply(block).build()
        )
    }

    fun build() = parts.toList()
}

@CoralDsl
class UrlPartListBuilder {
    private val parts = mutableListOf<PrototypeUrlPart>()

    fun path(value: String) {
        parts += PrototypeUrlPart.Path(PrototypeString.Inline(value))
    }

    fun path(block: PrototypeStringBuilder.() -> PrototypeString) {
        parts += PrototypeUrlPart.Path(PrototypeStringBuilder().block())
    }

    fun queryParameter(name: String, value: String) {
        parts += PrototypeUrlPart.QueryParameter(name, PrototypeString.Inline(value))
    }

    fun queryParameter(name: String, block: PrototypeStringBuilder.() -> PrototypeString) {
        parts += PrototypeUrlPart.QueryParameter(name, PrototypeStringBuilder().block())
    }

    fun build() = parts.toList()
}

data class BuiltAgentOption<T>(val name: String, val option: T) where T : AgentOption

inline fun <OptionType : PolymorphicAgentOption<ValueType, BackingType>, reified ValueType : PolymorphicAgentOptionValue<BackingType>, BackingType> BuiltAgentOption<OptionType>.tryGet(
    agent: SessionAgent
): BackingType? {
    val specifiedOptionValue = agent.graphAgent.options[name] ?: return null
    val specifiedValue = specifiedOptionValue.value as? ValueType ?: return null

    return specifiedValue.value
}

inline fun <OptionType : PolymorphicAgentOption<ValueType, BackingType>, reified ValueType : PolymorphicAgentOptionValue<BackingType>, BackingType> BuiltAgentOption<OptionType>.get(
    agent: SessionAgent
): BackingType =
    tryGet(agent) ?: throw IllegalArgumentException("Option \"$name\" was not set")


@CoralDsl
class RegistryAgentBuilder(
    var name: String,
) {
    var description: String = "example description"
    var version: String = "1.0.0"
    var registrySourceId: AgentRegistrySourceIdentifier = AgentRegistrySourceIdentifier.Local
    var readme: String = "example readme"
    var summary: String = "example summary"
    var license: RegistryAgentLicense = RegistryAgentLicense.Spdx("MIT")
    var runtimes: LocalAgentRuntimes = LocalAgentRuntimes()
    var path: Path? = null

    private val keywords: MutableSet<String> = mutableSetOf()
    private val links: MutableMap<String, String> = linkedMapOf()
    private val capabilities: MutableSet<AgentCapability> = mutableSetOf()
    private val options: MutableMap<String, AgentOption> = mutableMapOf()
    private val unresolvedExportSettings: MutableMap<RuntimeId, UnresolvedAgentExportSettings> = mutableMapOf()
    private val claimTypes: MutableList<RegistryAgentClaimType> = mutableListOf()
    private val dependencies: MutableList<RegistryAgentDependency> = mutableListOf()
    private var marketplace: RegistryAgentMarketplaceSettings? = null
    private var llm: AgentLlmConfig? = null

    fun link(name: String, value: String) {
        links[name] = value
    }

    fun keyword(keyword: String) {
        keywords.add(keyword)
    }

    fun capability(capability: AgentCapability) {
        capabilities.add(capability)
    }

    fun <T> option(name: String, value: T): BuiltAgentOption<T> where T : AgentOption {
        options[name] = value
        return BuiltAgentOption(name, value)
    }

    fun exportSetting(runtime: RuntimeId, value: UnresolvedAgentExportSettings) {
        unresolvedExportSettings[runtime] = value
    }

    fun claimType(name: String, description: String, dependencyName: String) {
        claimTypes += RegistryAgentClaimType(name = name, description = description, dependencyName = dependencyName)
    }

    fun dependency(name: String, vararg options: String) {
        dependencies += RegistryAgentDependency(name = name, options = options.toList())
    }

    fun marketplace(block: RegistryAgentMarketplaceSettingsBuilder.() -> Unit) =
        RegistryAgentMarketplaceSettingsBuilder().apply(block).build().also { marketplace = it }

    fun llm(block: AgentLlmConfigBuilder.() -> Unit) =
        AgentLlmConfigBuilder().apply(block).build().also { llm = it }

    fun stringOption(name: String, block: StringAgentOptionBuilder.() -> Unit) =
        option(name, StringAgentOptionBuilder().apply(block).build())

    fun stringListOption(name: String, block: StringListAgentOptionBuilder.() -> Unit) =
        option(name, StringListAgentOptionBuilder().apply(block).build())

    fun blobOption(name: String, block: BlobAgentOptionBuilder.() -> Unit) =
        option(name, BlobAgentOptionBuilder().apply(block).build())

    fun blobListOption(name: String, block: BlobListAgentOptionBuilder.() -> Unit) =
        option(name, BlobListAgentOptionBuilder().apply(block).build())

    fun booleanOption(name: String, block: BooleanAgentOptionBuilder.() -> Unit) =
        option(name, BooleanAgentOptionBuilder().apply(block).build())

    fun byteOption(name: String, block: ByteAgentOptionBuilder.() -> Unit) =
        option(name, ByteAgentOptionBuilder().apply(block).build())

    fun byteListOption(name: String, block: ByteListAgentOptionBuilder.() -> Unit) =
        option(name, ByteListAgentOptionBuilder().apply(block).build())

    fun shortOption(name: String, block: ShortAgentOptionBuilder.() -> Unit) =
        option(name, ShortAgentOptionBuilder().apply(block).build())

    fun shortListOption(name: String, block: ShortListAgentOptionBuilder.() -> Unit) =
        option(name, ShortListAgentOptionBuilder().apply(block).build())

    fun intOption(name: String, block: IntAgentOptionBuilder.() -> Unit) =
        option(name, IntAgentOptionBuilder().apply(block).build())

    fun intListOption(name: String, block: IntListAgentOptionBuilder.() -> Unit) =
        option(name, IntListAgentOptionBuilder().apply(block).build())

    fun longOption(name: String, block: LongAgentOptionBuilder.() -> Unit) =
        option(name, LongAgentOptionBuilder().apply(block).build())

    fun longListOption(name: String, block: LongListAgentOptionBuilder.() -> Unit) =
        option(name, LongListAgentOptionBuilder().apply(block).build())

    fun unsignedByteOption(name: String, block: UByteAgentOptionBuilder.() -> Unit) =
        option(name, UByteAgentOptionBuilder().apply(block).build())

    fun unsignedByteListOption(name: String, block: UByteListAgentOptionBuilder.() -> Unit) =
        option(name, UByteListAgentOptionBuilder().apply(block).build())

    fun unsignedShortOption(name: String, block: UShortAgentOptionBuilder.() -> Unit) =
        option(name, UShortAgentOptionBuilder().apply(block).build())

    fun unsignedShortListOption(name: String, block: UShortListAgentOptionBuilder.() -> Unit) =
        option(name, UShortListAgentOptionBuilder().apply(block).build())

    fun unsignedIntOption(name: String, block: UIntAgentOptionBuilder.() -> Unit) =
        option(name, UIntAgentOptionBuilder().apply(block).build())

    fun unsignedIntListOption(name: String, block: UIntListAgentOptionBuilder.() -> Unit) =
        option(name, UIntListAgentOptionBuilder().apply(block).build())

    fun unsignedLongOption(name: String, block: ULongAgentOptionBuilder.() -> Unit) =
        option(name, ULongAgentOptionBuilder().apply(block).build())

    fun unsignedLongListOption(name: String, block: ULongListAgentOptionBuilder.() -> Unit) =
        option(name, ULongListAgentOptionBuilder().apply(block).build())

    fun floatOption(name: String, block: FloatAgentOptionBuilder.() -> Unit) =
        option(name, FloatAgentOptionBuilder().apply(block).build())

    fun floatListOption(name: String, block: FloatListAgentOptionBuilder.() -> Unit) =
        option(name, FloatListAgentOptionBuilder().apply(block).build())

    fun doubleOption(name: String, block: DoubleAgentOptionBuilder.() -> Unit) =
        option(name, DoubleAgentOptionBuilder().apply(block).build())

    fun doubleListOption(name: String, block: DoubleListAgentOptionBuilder.() -> Unit) =
        option(name, DoubleListAgentOptionBuilder().apply(block).build())

    fun runtime(functionRuntime: FunctionRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = functionRuntime,
            prototypeRuntime = runtimes.prototypeRuntime
        )
    }

    fun debugRuntime(
        httpClient: HttpClient,
        body: suspend (client: Client, session: LocalSession, agent: SessionAgent) -> Unit
    ) {
        runtime(FunctionRuntime { executionContext, runtimeContext ->
            httpClient.streamableHttpFunctionRuntime(
                name,
                version
            ) { client, session ->
                body(client, session, executionContext.agent)
            }.execute(executionContext, runtimeContext)
        })
    }

    fun runtime(dockerRuntime: DockerRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = dockerRuntime,
            functionRuntime = runtimes.functionRuntime,
            prototypeRuntime = runtimes.prototypeRuntime
        )
    }

    fun runtime(executableRuntime: ExecutableRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = runtimes.functionRuntime,
            prototypeRuntime = runtimes.prototypeRuntime
        )
    }

    fun runtime(prototypeRuntime: PrototypeRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = runtimes.functionRuntime,
            prototypeRuntime = prototypeRuntime
        )
    }

    fun build(): RegistryAgent {
        return RegistryAgent(
            info = RegistryAgentInfo(
                description = description,
                capabilities = capabilities,
                identifier = RegistryAgentIdentifier(
                    name = name,
                    version = version,
                    registrySourceId = registrySourceId,
                ),
                readme = readme,
                summary = summary,
                license = license,
                keywords = keywords,
                links = links
            ),
            runtimes = runtimes,
            options = options,
            llm = llm,
            marketplace = marketplace,
            path = path,
            unresolvedExportSettings = unresolvedExportSettings,
            claimTypes = claimTypes,
            dependencies = dependencies,
        )
    }
}

@CoralDsl
class AgentLlmConfigBuilder {
    val proxies = mutableListOf<AgentLlmProxyRequest>()

    fun proxy(name: String, format: LlmProviderFormat, vararg models: String) {
        proxies += AgentLlmProxyRequest(name, format, models.toSet())
    }

    fun build() = AgentLlmConfig(proxies = proxies.toList())
}

@CoralDsl
class RegistryAgentMarketplaceSettingsBuilder {
    private var pricing: RegistryAgentMarketplacePricing? = null
    private var identities: RegistryAgentMarketplaceIdentities? = null

    fun pricing(
        description: String,
        recommendations: RegistryAgentMarketplacePricingRecommendations,
        block: RegistryAgentMarketplacePricingBuilder.() -> Unit
    ) {
        pricing = RegistryAgentMarketplacePricingBuilder(description, recommendations).apply(block).build()
    }

    fun identities(block: RegistryAgentMarketplaceIdentitiesBuilder.() -> Unit) {
        identities = RegistryAgentMarketplaceIdentitiesBuilder().apply(block).build()
    }

    fun build(): RegistryAgentMarketplaceSettings {
        return RegistryAgentMarketplaceSettings(
            pricing = pricing,
            identities = identities
        )
    }
}

@CoralDsl
class RegistryAgentMarketplacePricingBuilder(
    val description: String,
    val recommendations: RegistryAgentMarketplacePricingRecommendations
) {
    var currency: String = "USD"

    fun build(): RegistryAgentMarketplacePricing {
        return RegistryAgentMarketplacePricing(
            description = description,
            recommendations = recommendations,
            currency = currency
        )
    }
}

@CoralDsl
class RegistryAgentMarketplaceIdentitiesBuilder {
    private var erc8004: RegistryAgentMarketplaceIdentityErc8004? = null

    fun erc8004(wallet: String, block: RegistryAgentMarketplaceIdentityErc8004Builder.() -> Unit) {
        erc8004 = RegistryAgentMarketplaceIdentityErc8004Builder(wallet).apply(block).build()
    }

    fun build(): RegistryAgentMarketplaceIdentities =
        RegistryAgentMarketplaceIdentities(erc8004 = erc8004)
}

@CoralDsl
class RegistryAgentMarketplaceIdentityErc8004Builder(val wallet: String) {
    private val endpoints: MutableList<Erc8004Endpoint> = mutableListOf()

    fun endpoint(name: String, endpoint: String) {
        endpoints += Erc8004Endpoint(name = name, endpoint = endpoint)
    }

    fun build(): RegistryAgentMarketplaceIdentityErc8004 {
        return RegistryAgentMarketplaceIdentityErc8004(
            wallet = wallet,
            endpoints = endpoints.toList()
        )
    }
}

fun registryAgent(name: String, block: RegistryAgentBuilder.() -> Unit = {}): RegistryAgent =
    RegistryAgentBuilder(name).apply(block).build()

fun composedString(separator: String = "", block: PrototypeStringListBuilder.() -> Unit): PrototypeString {
    return PrototypeString.ComposedString(
        parts = PrototypeStringListBuilder().apply(block).build(),
        separator = separator
    )
}

fun composedUrl(base: String, block: UrlPartListBuilder.() -> Unit): PrototypeString {
    return PrototypeString.ComposedUrl(
        base = base,
        parts = UrlPartListBuilder().apply(block).build()
    )
}