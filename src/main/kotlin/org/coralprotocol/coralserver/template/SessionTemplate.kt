package org.coralprotocol.coralserver.template

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.SessionRequest

@Serializable
data class SessionTemplateParameter(
    val key: String,
    val label: String,
    val description: String,
    val required: Boolean = true,
    val secret: Boolean = false,
    val default: String? = null,
    val choices: List<String>? = null,
)

@Serializable
data class SessionTemplateInfo(
    val slug: String,
    val name: String,
    val description: String,
    val category: String,
    val agentCount: Int,
    val estimatedDuration: String,
    val estimatedCost: String,
    val parameters: List<SessionTemplateParameter>,
)

@Serializable
data class TemplateLaunchRequest(
    val parameters: Map<String, String> = mapOf(),
    val namespace: String = "default",
    val registrySource: String = "marketplace",
    val runtime: String = "docker",
)

interface SessionTemplate {
    val info: SessionTemplateInfo

    fun buildSessionRequest(
        parameters: Map<String, String>,
        namespace: String,
        registrySource: AgentRegistrySourceIdentifier,
        runtime: RuntimeId,
    ): SessionRequest
}

fun parseRegistrySource(source: String): AgentRegistrySourceIdentifier = when (source) {
    "local" -> AgentRegistrySourceIdentifier.Local
    "marketplace" -> AgentRegistrySourceIdentifier.Marketplace
    else -> throw IllegalArgumentException("Unknown registry source: '$source'. Must be 'local' or 'marketplace'")
}

fun parseRuntimeId(runtime: String): RuntimeId = when (runtime) {
    "executable" -> RuntimeId.EXECUTABLE
    "docker" -> RuntimeId.DOCKER
    "function" -> RuntimeId.FUNCTION
    else -> throw IllegalArgumentException("Unknown runtime: '$runtime'. Must be 'executable', 'docker', or 'function'")
}

fun validateTemplateParameters(parameters: Map<String, String>, info: SessionTemplateInfo) {
    for (parameter in info.parameters) {
        if (parameter.required && parameters[parameter.key].isNullOrBlank()) {
            error("Missing required parameter: '${parameter.key}'")
        }

        val value = parameters[parameter.key]
        if (value != null && parameter.choices != null && value !in parameter.choices) {
            error("Invalid value '$value' for parameter '${parameter.key}'. Must be one of: ${parameter.choices}")
        }
    }
}
