package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.value
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.mcpFunctionRuntime
import org.koin.core.component.KoinComponent

interface DebugAgentIdHolder {
    val identifier: RegistryAgentIdentifier
}

abstract class DebugAgent(protected val client: HttpClient) : KoinComponent {
    abstract val companion: DebugAgentIdHolder
    abstract val options: Map<String, AgentOption>
    abstract val description: String
    abstract val exportSettings: UnresolvedAgentExportSettingsMap
    val genericExportSettings = mapOf(
        RuntimeId.FUNCTION to UnresolvedAgentExportSettings(
            quantity = 1u,
            pricing = RegistryAgentExportPricing(
                minPrice = AgentClaimAmount.Usd(2.0),
                maxPrice = AgentClaimAmount.Usd(5.0)
            ),
            options = mapOf()
        )
    )

    abstract suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    )

    protected inline fun <reified T> getOption(agent: SessionAgent, optionName: String): T?
            where T : AgentOptionValue {
        val option = agent.graphAgent.options[optionName]
            ?: return null

        val value = option.value()
        if (option.value() !is T)
            throw IllegalStateException("Option $optionName has the wrong type")

        return value as T
    }

    protected inline fun <reified T> getRequiredOption(agent: SessionAgent, optionName: String): T
            where T : AgentOptionValue {
        return getOption<T>(agent, optionName)
            ?: throw IllegalStateException("Missing required option $optionName")
    }

    fun generate(
        export: Boolean = false
    ): RegistryAgent {
        return RegistryAgent(
            info = RegistryAgentInfo(
                description = description,
                capabilities = setOf(),
                identifier = companion.identifier
            ),
            runtimes = LocalAgentRuntimes(
                functionRuntime = FunctionRuntime { executionContext, runtimeContext ->
                    client.mcpFunctionRuntime(companion.identifier.name, companion.identifier.name) { client, session ->
                        execute(client, session, executionContext.agent)
                    }.execute(executionContext, runtimeContext)
                }
            ),
            options = options,
            unresolvedExportSettings = if (export) exportSettings else mapOf()
        )
    }
}