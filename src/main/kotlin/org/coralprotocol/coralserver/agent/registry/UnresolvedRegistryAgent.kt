@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val AGENT_FILE = "coral-agent.toml"

@Serializable
abstract class UnresolvedRegistryAgent(
    @SerialName("export")
    var unresolvedExportSettings: UnresolvedAgentExportSettingsMap = mapOf()
) {
    abstract fun resolve(context: AgentResolutionContext): List<RegistryAgent>
}