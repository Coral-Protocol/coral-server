package org.coralprotocol.coralserver.agent.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

private val logger = KotlinLogging.logger {}

@Serializable
data class UnresolvedAgentExport(
    val quantity: UInt,

    // resolves to List<RuntimeId>, maybe make this type a List<RuntimeId> when ktoml supports lists of enums:
    // https://github.com/orchestr7/ktoml/issues/340
    val runtimes: Map<String, AgentExportPricing>

    // todo: pricing here
) {
    fun resolve(name: String, agent: RegistryAgent): AgentExport {
        if (quantity == 0u) {
            throw RegistryException("Cannot export 0 \"$name\" agents")
        }

        // Runtimes must be either Executable, Docker or Phala and the runtime must be defined on the imported agent
        // todo: disallow exporting of Executable runtimes?
        val validRuntimes = runtimes.mapNotNull { (runtimeName, pricing) ->
            try {
                val runtimeId = RuntimeId.valueOf(runtimeName.toUpperCasePreservingASCIIRules())
                if (agent.runtimes.getById(runtimeId) == null) {
                    logger.warn { "Runtime \"$runtimeName\" is not defined for agent \"$name\"" }
                    null
                } else {
                    runtimeId to pricing
                }
            }
            catch (_: IllegalArgumentException) {
                logger.warn { "Invalid runtime \"$runtimeName\" for agent \"$name\"" }
                null
            }
        }.toMap()

        if (validRuntimes.isEmpty()) {
            throw RegistryException("Cannot export agent \"$name\" with no runtimes")
        }

        return AgentExport(agent, validRuntimes, quantity)
    }
}