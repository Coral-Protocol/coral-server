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
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
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
    val options: Map<String, AgentOption> = mapOf(),

    @Description("Information for this agent relevant to it's potential listing on the marketplace")
    @Optional
    val marketplace: RegistryAgentMarketplaceSettings? = null
) : KoinComponent {
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

        options.forEach { (key, option) ->
            option.issueConfigurationWarnings(edition, context, key)
        }

        val registryAgent = RegistryAgent(
            edition = edition,
            info = agentInfo.resolve(context.registrySourceIdentifier),
            runtimes = runtimes,
            options = options,
            path = context.path,
            marketplace = marketplace
        )
        registryAgent.validate()

        return registryAgent
    }
}
