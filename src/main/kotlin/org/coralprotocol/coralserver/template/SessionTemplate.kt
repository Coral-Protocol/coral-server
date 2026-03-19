package org.coralprotocol.coralserver.template

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.*

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
    else -> throw IllegalArgumentException("Unknown runtime: '$runtime'. Must be 'executable' or 'docker'")
}

fun sessionTemplateAgent(
    agentName: String,
    agentVersion: String,
    registrySource: AgentRegistrySourceIdentifier,
    instanceName: String,
    systemPrompt: String,
    options: Map<String, AgentOptionValue>,
    runtime: RuntimeId = RuntimeId.DOCKER,
    plugins: Set<GraphAgentPlugin> = setOf(),
    description: String? = null,
) = GraphAgentRequest(
    id = RegistryAgentIdentifier(
        name = agentName,
        version = agentVersion,
        registrySourceId = registrySource,
    ),
    name = instanceName,
    description = description,
    options = options,
    systemPrompt = systemPrompt,
    provider = GraphAgentProvider.Local(runtime),
    plugins = plugins,
)

fun validateTemplateParameters(parameters: Map<String, String>, info: SessionTemplateInfo) {
    for (param in info.parameters) {
        if (param.required && parameters[param.key].isNullOrBlank()) {
            error("Missing required parameter: '${param.key}'")
        }
        val value = parameters[param.key]
        if (value != null && param.choices != null && value !in param.choices) {
            error("Invalid value '$value' for parameter '${param.key}'. Must be one of: ${param.choices}")
        }
    }
}

val COMMON_LLM_PARAMETERS = listOf(
    SessionTemplateParameter(
        key = "MODEL_API_KEY",
        label = "LLM API Key",
        description = "API key for your LLM provider (e.g. OpenAI key starting with sk-...)",
        required = true,
        secret = true,
    ),
    SessionTemplateParameter(
        key = "MODEL_PROVIDER",
        label = "Model Provider",
        description = "The LLM provider to use",
        required = false,
        default = "openai",
        choices = listOf("openai", "anthropic", "openrouter"),
    ),
    SessionTemplateParameter(
        key = "MODEL_NAME",
        label = "Model Name",
        description = "The model to use for all agents",
        required = false,
        default = "gpt-5-mini",
    ),
)

fun buildCommonAgentOptions(
    parameters: Map<String, String>,
    maxIterations: String,
): Map<String, AgentOptionValue> = mapOf(
    "MODEL_API_KEY" to AgentOptionValue.String(parameters["MODEL_API_KEY"] ?: ""),
    "MODEL_PROVIDER" to AgentOptionValue.String(parameters.getOrDefault("MODEL_PROVIDER", "openai")),
    "MODEL_NAME" to AgentOptionValue.String(parameters.getOrDefault("MODEL_NAME", "gpt-5-mini")),
    "MAX_ITERATIONS" to AgentOptionValue.String(maxIterations),
)

fun sessionTemplateRequest(
    agents: List<GraphAgentRequest>,
    groups: Set<Set<String>>,
    namespace: String,
    ttlMs: Long = 300_000,
    annotations: Map<String, String> = mapOf(),
) = SessionRequest(
    agentGraphRequest = AgentGraphRequest(
        agents = agents,
        groups = groups,
    ),
    namespaceProvider = SessionNamespaceProvider.CreateIfNotExists(
        namespaceRequest = SessionNamespaceRequest(
            name = namespace,
        )
    ),
    execution = SessionRequestExecution.Execute(
        runtimeSettings = SessionRuntimeSettings(
            ttl = ttlMs,
            extendedEndReport = true,
            persistenceMode = SessionPersistenceMode.HoldAfterExit(duration = 300_000),
        )
    ),
    annotations = annotations,
)
