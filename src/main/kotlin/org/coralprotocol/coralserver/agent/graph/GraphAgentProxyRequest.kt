package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class GraphAgentProxyRequest(
    @Description("The name of the LLM proxy configuration")
    val configurationName: String,

    @Description("The name of the model")
    val modelName: String
)