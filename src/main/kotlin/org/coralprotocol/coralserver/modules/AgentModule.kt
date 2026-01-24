package org.coralprotocol.coralserver.modules

import org.coralprotocol.coralserver.agent.debug.EchoDebugAgent
import org.coralprotocol.coralserver.agent.debug.PuppetDebugAgent
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.debug.ToolDebugAgent
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.config.RegistryConfig
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val agentModule = module {
    singleOf(::EchoDebugAgent)
    singleOf(::SeedDebugAgent)
    singleOf(::ToolDebugAgent)
    singleOf(::PuppetDebugAgent)

    single(createdAtStart = true) {
        val config: RegistryConfig = get()
        AgentRegistry {
            if (config.enableMarketplaceAgentRegistrySource)
                addMarketplaceSource()

            config.localAgents.forEach {
                logger.info { "watching for agents matching pattern: $it" }
                addFileBasedSource(it, config.watchLocalAgents, config.localAgentRescanTimer)
            }

            if (config.includeDebugAgents) {
                addLocalAgents(
                    "debug agents",
                    listOf(
                        get<EchoDebugAgent>().generate(),
                        get<SeedDebugAgent>().generate(),
                        get<ToolDebugAgent>().generate(),
                        get<PuppetDebugAgent>().generate()
                    )
                )
            }
        }
    }

    single(createdAtStart = true) {
        McpToolManager(get(named(LOGGER_CONFIG)))
    }
}