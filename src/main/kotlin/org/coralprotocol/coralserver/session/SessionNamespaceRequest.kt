package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable

@Serializable
data class SessionNamespaceRequest(
    @Description("The name of this namespace")
    val name: String,

    @Description(
        """
        If this is true the namespace will be deleted when the last session in this namespace closes.  Note that this
        does not guarantee that the namespace will never be empty, because namespaces can be created without sessions
    """
    )
    @Optional
    val deleteOnLastSessionExit: Boolean = true,

    @Optional
    override val annotations: Map<String, String> = mapOf(),
) : SessionResource
