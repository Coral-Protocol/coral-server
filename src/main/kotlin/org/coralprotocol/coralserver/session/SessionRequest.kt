@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest

@Serializable
@JsonClassDiscriminator("mode")
sealed interface SessionRequestExecution {
    @Serializable
    @SerialName("execute_now")
    @Description("The session should be executed immediately with the specified runtime settings")
    data class Execute(
        @Optional
        val runtimeSettings: SessionRuntimeSettings = SessionRuntimeSettings(),
    ) : SessionRequestExecution


    @Serializable
    @SerialName("defer")
    @Description("The session's execution is deferred.  A deferred execution must be executed manually later")
    object Defer : SessionRequestExecution
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionNamespaceRequest {
    @Serializable
    @SerialName("use_existing")
    @Description("Indicates that the session request should use an existing namespace")
    data class UseExisting(
        val name: String
    ) : SessionNamespaceRequest

    @Serializable
    @SerialName("create_if_not_exists")
    @Description(
        """
        Provides a full namespace request to create an ad hoc namespace if the namespace doesn't already exist.  Note
        that this will not update an existing namespace, so for example, there is no guarantee that annotations 
        specified on this request end up on the namespace that this session belongs in.
        """
    )
    data class CreateIfNotExists(
        val builder: SessionNamespaceBuilder
    ) : SessionNamespaceRequest
}

@Serializable
data class SessionRequest(
    @Description("A request for the agents in this session")
    val agentGraphRequest: AgentGraphRequest,

    @Description("A description of what namespace this session should run in")
    val namespaceRequest: SessionNamespaceRequest,

    @Optional
    val execution: SessionRequestExecution = SessionRequestExecution.Execute(),

    @Optional
    override val annotations: Map<String, String> = mapOf()
) : SessionResource