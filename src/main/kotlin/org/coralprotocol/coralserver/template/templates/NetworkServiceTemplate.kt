package org.coralprotocol.coralserver.template.templates

import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.SessionNamespaceProvider
import org.coralprotocol.coralserver.session.SessionNamespaceRequest
import org.coralprotocol.coralserver.session.SessionRequest
import org.coralprotocol.coralserver.session.SessionRequestExecution
import org.coralprotocol.coralserver.template.SessionTemplate
import org.coralprotocol.coralserver.template.SessionTemplateInfo

object NetworkServiceTemplate : SessionTemplate {
    override val info = SessionTemplateInfo(
        slug = "network-service",
        name = "Network Service",
        description = "Compatibility placeholder template for mock network-service launches on branches that do not yet include the full implementation.",
        category = "compatibility",
        agentCount = 0,
        estimatedDuration = "deferred",
        estimatedCost = "$0.00",
        parameters = emptyList(),
    )

    override fun buildSessionRequest(
        parameters: Map<String, String>,
        namespace: String,
        registrySource: AgentRegistrySourceIdentifier,
        runtime: RuntimeId,
    ): SessionRequest {
        return SessionRequest(
            agentGraphRequest = AgentGraphRequest(
                agents = emptyList(),
                groups = emptySet(),
            ),
            namespaceProvider = SessionNamespaceProvider.CreateIfNotExists(
                SessionNamespaceRequest(name = namespace)
            ),
            execution = SessionRequestExecution.Defer,
            annotations = mapOf(
                "template" to info.slug,
                "templateMode" to "placeholder",
            ) + parameters,
        )
    }
}
