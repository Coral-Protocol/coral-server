package org.coralprotocol.coralserver.dsl

import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeUrlPart
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
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

    fun option(key: String, value: AgentOption) {
        options[key] = value
    }

    fun exportSetting(runtime: RuntimeId, value: UnresolvedAgentExportSettings) {
        unresolvedExportSettings[runtime] = value
    }

    fun marketplace(block: RegistryAgentMarketplaceSettingsBuilder.() -> Unit) {
        marketplace = RegistryAgentMarketplaceSettingsBuilder().apply(block).build()
    }

    fun llm(block: AgentLlmConfigBuilder.() -> Unit) {
        llm = AgentLlmConfigBuilder().apply(block).build()
    }

    fun runtime(functionRuntime: FunctionRuntime) {
        runtimes = runtimes.copy(functionRuntime = functionRuntime)
    }

    fun runtime(dockerRuntime: DockerRuntime) {
        runtimes = runtimes.copy(dockerRuntime = dockerRuntime)
    }

    fun runtime(executableRuntime: ExecutableRuntime) {
        runtimes = runtimes.copy(executableRuntime = executableRuntime)
    }

    fun runtime(prototypeRuntime: PrototypeRuntime) {
        runtimes = runtimes.copy(prototypeRuntime = prototypeRuntime)
    }

    fun build(): RegistryAgent {
        return RegistryAgent(
            info = RegistryAgentInfo(
                capabilities = capabilities.toSet(),
                identifier = RegistryAgentIdentifier(
                    name = name,
                    version = version,
                    registrySourceId = registrySourceId
                ),
                description = description,
                readme = readme,
                summary = summary,
                license = license,
                keywords = keywords.toSet(),
                links = links.toMap()
            ),
            runtimes = runtimes,
            options = options.toMap(),
            llm = llm,
            marketplace = marketplace,
            path = path,
            unresolvedExportSettings = unresolvedExportSettings.toMap()
        )
    }
}

@CoralDsl
class AgentLlmConfigBuilder {
    private val proxies = mutableListOf<AgentLlmProxyRequest>()

    fun proxy(name: String, format: LlmProviderFormat, vararg models: String) {
        proxies += AgentLlmProxyRequest(
            name = name,
            format = format,
            models = models.toSet()
        )
    }

    fun build() = AgentLlmConfig(
        proxies = proxies.toList()
    )
}

@CoralDsl
class RegistryAgentMarketplaceSettingsBuilder {
    private var pricing: RegistryAgentMarketplacePricing? = null
    private var identities: RegistryAgentMarketplaceIdentities? = null
    private val keywords: MutableSet<String> = mutableSetOf()

    fun keyword(keyword: String) {
        keywords.add(keyword)
    }

    fun pricing(
        description: String,
        recommendations: RegistryAgentMarketplacePricingRecommendations,
        block: RegistryAgentMarketplacePricingBuilder.() -> Unit = {}
    ) {
        pricing = RegistryAgentMarketplacePricingBuilder(description, recommendations).apply(block).build()
    }

    fun identities(block: RegistryAgentMarketplaceIdentitiesBuilder.() -> Unit) {
        identities = RegistryAgentMarketplaceIdentitiesBuilder().apply(block).build()
    }

    fun build(): RegistryAgentMarketplaceSettings {
        return RegistryAgentMarketplaceSettings(
            keywords = keywords.toSet(),
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
    fun build(): RegistryAgentMarketplacePricing {
        return RegistryAgentMarketplacePricing(
            description = description,
            recommendations = recommendations
        )
    }
}

@CoralDsl
class RegistryAgentMarketplaceIdentitiesBuilder {
    private var erc8004: RegistryAgentMarketplaceIdentityErc8004? = null

    fun erc8004(wallet: String, block: RegistryAgentMarketplaceIdentityErc8004Builder.() -> Unit) {
        erc8004 = RegistryAgentMarketplaceIdentityErc8004Builder(wallet).apply(block).build()
    }

    fun build(): RegistryAgentMarketplaceIdentities {
        return RegistryAgentMarketplaceIdentities(
            erc8004 = erc8004 ?: error("erc8004 is required")
        )
    }
}

@CoralDsl
class RegistryAgentMarketplaceIdentityErc8004Builder(val wallet: String) {
    private val endpoints: MutableMap<String, String> = mutableMapOf()

    fun endpoint(name: String, endpoint: String) {
        endpoints[name] = endpoint
    }

    fun build(): RegistryAgentMarketplaceIdentityErc8004 {
        return RegistryAgentMarketplaceIdentityErc8004(
            wallet = wallet,
            endpoints = endpoints.map { (name, endpoint) -> Erc8004Endpoint(name, endpoint) }
        )
    }
}

fun registryAgent(name: String, block: RegistryAgentBuilder.() -> Unit = {}): RegistryAgent =
    RegistryAgentBuilder(name).apply(block).build()

fun composedString(separator: String = "", block: PrototypeStringListBuilder.() -> Unit): PrototypeString =
    PrototypeString.ComposedString(PrototypeStringListBuilder().apply(block).build(), separator)

fun composedUrl(base: String, block: UrlPartListBuilder.() -> Unit): PrototypeString =
    PrototypeString.ComposedUrl(base, UrlPartListBuilder().apply(block).build())
