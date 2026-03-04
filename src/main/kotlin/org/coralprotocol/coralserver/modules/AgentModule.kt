package org.coralprotocol.coralserver.modules

import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.debug.*
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
    singleOf(::SocketDebugAgent)

    single(createdAtStart = true) {
        val config: RegistryConfig = get()
        AgentRegistry {
            if (config.enableMarketplaceAgentRegistrySource) {
                runBlocking {
                    addMarketplaceSource()
                }
            }

            config.localAgents.forEach {
                logger.trace { "watching for agents matching pattern: $it" }
                addFileBasedSource(it, config.watchLocalAgents, config.localAgentRescanTimer)
            }

            if (config.includeDebugAgents) {
                addLocalAgents(
                    "debug agents",
                    listOf(
                        get<EchoDebugAgent>().generate(),
                        get<SeedDebugAgent>().generate(),
                        get<ToolDebugAgent>().generate(),
                        get<PuppetDebugAgent>().generate(),
                        get<SocketDebugAgent>().generate()
                    )
                )
            }
        }
    }

    single(createdAtStart = true) {
        McpToolManager(get(named(LOGGER_CONFIG)))
    }
}