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
import java.nio.file.Path

val agentModule = module {
    singleOf(::EchoDebugAgent)
    singleOf(::SeedDebugAgent)
    singleOf(::ToolDebugAgent)
    singleOf(::PuppetDebugAgent)

    single(createdAtStart = true) {
        val config: RegistryConfig = get()
        AgentRegistry {
            if (config.enableMarketplaceAgentRegistrySource)
                addMarketplace()

            config.localRegistries.forEach { addLocal(Path.of(it)) }

            if (config.includeDebugAgents) {
                addLocalAgents(
                    listOf(
                        get<EchoDebugAgent>().generate(),
                        get<SeedDebugAgent>().generate(),
                        get<ToolDebugAgent>().generate(),
                        get<PuppetDebugAgent>().generate()
                    ),
                    "debug agents"
                )
            }
        }
    }

    single(createdAtStart = true) {
        McpToolManager(get(named(LOGGER_CONFIG)))
    }
}