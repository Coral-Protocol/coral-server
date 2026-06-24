package org.coralprotocol.coralserver.agent.debug

import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.dsl.registryAgent
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration

const val PUPPET_AGENT_ID = "puppet"
const val PUPPET_AGENT_VERSION = "1.0.1"
val PUPPET_AGENT_IDENTIFIER =
    RegistryAgentIdentifier(PUPPET_AGENT_ID, PUPPET_AGENT_VERSION, AgentRegistrySourceIdentifier.Local)

val puppetAgentModule = module {
    single(named(PUPPET_AGENT_ID)) {
        registryAgent(PUPPET_AGENT_ID) {
            version = PUPPET_AGENT_VERSION

            description = "Debug agent that performs no actions"
            summary = description
            readme = """
                This is a dummy agent that performs no actions on it's own.  It is designed as dedicated a host for the console's puppet feature.
                
                This agent will never exit naturally.
                
                This description should be overridden in the session request!
            """.trimIndent()

            debugRuntime(get()) { _, _, _ ->
                delay(Duration.INFINITE)
            }
        }
    }
}