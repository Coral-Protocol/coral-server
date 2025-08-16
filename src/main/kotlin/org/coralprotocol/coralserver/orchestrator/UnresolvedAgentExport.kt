package org.coralprotocol.coralserver.orchestrator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.toUpperCasePreservingASCIIRules
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.runtime.RuntimeId

val logger = KotlinLogging.logger {}

@Serializable
data class UnresolvedAgentExport(
    val quantity: UInt,

    // resolves to List<RuntimeId>, maybe make this type a List<RuntimeId> when ktoml supports lists of enums:
    // https://github.com/orchestr7/ktoml/issues/340
    val runtimes: List<String>

    // todo: pricing here
) {
    fun resolve(name: String, agent: RegistryAgent): AgentExport {
        if (quantity == 0u) {
            throw RegistryException("Cannot export 0 \"$name\" agents")
        }

        // Runtimes must be either Executable, Docker or Phala and the runtime must be defined on the imported agent
        // todo: disallow exporting of Executable runtimes?
        val validRuntimes = runtimes.mapNotNull {
            try {
                val runtimeId = RuntimeId.valueOf(it.toUpperCasePreservingASCIIRules())
                if (agent.runtime.getById(runtimeId) == null) {
                    logger.warn { "Runtime \"$it\" is not defined for agent \"$name\"" }
                    null
                } else {
                    runtimeId
                }
            }
            catch (_: IllegalArgumentException) {
                logger.warn { "Invalid runtime \"$it\" for agent \"$name\"" }
                null
            }
        }

        if (validRuntimes.isEmpty()) {
            throw RegistryException("Cannot export agent \"$name\" with no runtimes")
        }

        return AgentExport(agent, validRuntimes, quantity)
    }
}