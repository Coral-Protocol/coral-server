package org.coralprotocol.coralserver.agent.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes

private val logger = KotlinLogging.logger {}

@Serializable
data class UnresolvedInlineRegistryAgent(
    @Description("The version of this agent")
    val edition: Int = 1,

    @SerialName("agent")
    val agentInfo: RegistryAgentInfo,

    @Description("The runtimes that this agent supports")
    val runtimes: LocalAgentRuntimes,

    @Description("The options that this agent supports, for example the API keys required for the agent to function")
    val options: Map<String, AgentOption>,
) : UnresolvedRegistryAgent() {
    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        if (edition < FIRST_AGENT_EDITION) {
            throw RegistryException("Agent ${context.path} has invalid edition '$edition', must be at least $FIRST_AGENT_EDITION")
        }
        else if (edition > CURRENT_AGENT_EDITION) {
            throw RegistryException("Agent ${context.path} has edition '$edition', this server's highest supported edition is '$CURRENT_AGENT_EDITION'")
        }

        if (edition == FIRST_AGENT_EDITION) {
            logger.warn { "Agent ${context.path} has out of date edition $edition.  Current edition is $CURRENT_AGENT_EDITION" }
        }

        options.forEach { (key, option) ->
            option.issueConfigurationWarnings(edition, context, key)
        }

        return listOf(RegistryAgent(
            info = agentInfo,
            runtimes = runtimes,
            options = options,
            unresolvedExportSettings = unresolvedExportSettings,
            path = context.path
        ))
    }
}
