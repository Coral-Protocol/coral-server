package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.time.Duration

class PuppetDebugAgent(client: HttpClient) : DebugAgent(client) {
    override val companion: DebugAgentIdHolder
        get() = Companion

    companion object : DebugAgentIdHolder {
        override val identifier: RegistryAgentIdentifier
            get() = RegistryAgentIdentifier("puppet", "1.0.0", AgentRegistrySourceIdentifier.Local)
    }

    override val options: Map<String, AgentOption>
        get() = mapOf()

    override val description: String
        get() = """
            This is a dummy agent that performs no actions on it's own.  It is designed as dedicated a host for the console's puppet feature.
            
            This agent will never exit naturally.
            
            This description should be overridden in the session request!
        """.trimIndent()

    override val exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>
        get() = genericExportSettings

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        // hmm
        delay(Duration.INFINITE)
    }
}