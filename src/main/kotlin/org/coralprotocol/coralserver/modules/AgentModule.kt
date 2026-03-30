package org.coralprotocol.coralserver.modules

import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.debug.*
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
    singleOf(::SocketDebugAgent)

    single(createdAtStart = true) {
        val config: RegistryConfig = get()
        AgentRegistry {
            if (config.enableMarketplaceAgentRegistrySource) {
                runBlocking {
                    addMarketplaceSource()
                }
            }

            val allAgentSources = (config.localAgents + if (config.includeCoralHomeAgents) {
                listOf(
                    // Support separation by agent version
                    "${Path.of(System.getProperty("user.home"), ".coral", "agents")}/*/*",
                    // For agents manually added it's more natural that they aren't separated by version
                    "${Path.of(System.getProperty("user.home"), ".coral", "agents")}/*",
                    // Specific directory that the coralizer should put links in to clearly separate manually managed
                    "${Path.of(System.getProperty("user.home"), ".coral", "agents")}/locallinked/*/*",
                )
            } else {
                emptyList()
            }).distinct()

            allAgentSources.forEach {
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