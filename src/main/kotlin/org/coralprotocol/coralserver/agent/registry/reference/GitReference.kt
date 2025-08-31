package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext

/**
 * An agent referenced by a Git repository
 */
@Serializable
data class GitReference (
    val git: String,
    val branch: String? = null,
    val tag: String? = null,
    val rev: String? = null,
) : UnresolvedRegistryAgentReference {
    override fun resolve(
        context: RegistryResolutionContext,
        name: String
    ): RegistryAgent {
        TODO("Not yet implemented")
    }
}