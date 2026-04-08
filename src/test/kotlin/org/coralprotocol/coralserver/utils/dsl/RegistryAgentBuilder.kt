package org.coralprotocol.coralserver.utils.dsl

import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.agent.runtime.prototype.*
import java.nio.file.Path

@TestDsl
class PrototypeStringBuilder {
    fun inline(value: String): PrototypeString = PrototypeString.Inline(value)
    fun option(name: String): PrototypeString = PrototypeString.Option(name)
}

@TestDsl
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

@TestDsl
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

@TestDsl
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


    fun runtime(functionRuntime: FunctionRuntime) {
        runtimes = LocalAgentRuntimes(
            executableRuntime = runtimes.executableRuntime,
            dockerRuntime = runtimes.dockerRuntime,
            functionRuntime = functionRuntime,
            prototypeRuntime = runtimes.prototypeRuntime
        )
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
            path = path,
            unresolvedExportSettings = unresolvedExportSettings,
            marketplace = marketplace
        )
    }
}

@TestDsl
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

@TestDsl
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

@TestDsl
class RegistryAgentMarketplaceIdentitiesBuilder {
    private var erc8004: RegistryAgentMarketplaceIdentityErc8004? = null

    fun erc8004(wallet: String, block: RegistryAgentMarketplaceIdentityErc8004Builder.() -> Unit) {
        erc8004 = RegistryAgentMarketplaceIdentityErc8004Builder(wallet).apply(block).build()
    }

    fun build(): RegistryAgentMarketplaceIdentities =
        RegistryAgentMarketplaceIdentities(erc8004 = erc8004)
}

@TestDsl
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