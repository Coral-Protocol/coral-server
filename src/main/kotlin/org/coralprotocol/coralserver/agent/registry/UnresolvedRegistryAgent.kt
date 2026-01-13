package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

const val AGENT_FILE = "coral-agent.toml"

@Serializable
data class UnresolvedRegistryAgent(
    @Description("The version of this agent")
    val edition: Int = 1,

    @SerialName("agent")
    val agentInfo: UnresolvedRegistryAgentInfo,

    @Description("The runtimes that this agent supports")
    val runtimes: LocalAgentRuntimes,

    @Description("The options that this agent supports, for example the API keys required for the agent to function")
    val options: Map<String, AgentOption>,
) : KoinComponent {
    private val logger by inject<Logger>(named(LOGGER_CONFIG))

    fun resolve(context: AgentResolutionContext): RegistryAgent {
        if (edition < FIRST_AGENT_EDITION) {
            throw RegistryException("Agent ${context.path} has invalid edition '$edition', must be at least $FIRST_AGENT_EDITION")
        } else if (edition > CURRENT_AGENT_EDITION) {
            throw RegistryException("Agent ${context.path} has edition '$edition', this server's highest supported edition is '$CURRENT_AGENT_EDITION'")
        }

        if (edition == FIRST_AGENT_EDITION) {
            logger.warn { "Agent ${context.path} has out of date edition $edition.  Current edition is $CURRENT_AGENT_EDITION" }
        }

        options.forEach { (key, option) ->
            option.issueConfigurationWarnings(edition, context, key)
        }

        return RegistryAgent(
            info = agentInfo.resolve(context.registrySourceIdentifier),
            runtimes = runtimes,
            options = options,
            path = context.path
        )
    }
}
