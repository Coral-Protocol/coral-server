package org.coralprotocol.coralserver.agent.registry.reference

import com.github.syari.kgit.KGit
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.routes.api.v1.filterNotNullValues
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

/**
 * An agent referenced by a Git repository
 */
@Serializable
@SerialName("git")
data class GitUnresolvedRegistryAgent (
    val repo: String,
    val branch: String? = null,
    val tag: String? = null,
    val rev: String? = null,
) : UnresolvedRegistryAgent() {
    @Transient
    private val encoder = Base64.getUrlEncoder()

    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        val safeRepoName = encoder.encodeToString(repo.toByteArray())
        val identifiers = mapOf(
            "branch" to branch,
            "tag" to tag,
            "rev" to rev,
        ).filterNotNullValues()

        // Instead of some arcane priority logic, it will be an error if more than one identifier is specified.
        if (identifiers.size > 1) {
            throw RegistryException("git-agent (repo $repo) must only specify one of branch, tag, or rev")
        }

        // Also, to ensure the user puts a bit of thought into the version of the agent they are requesting, at least
        // one identifier must be specified.
        val (idType, idValue) = identifiers.toList().firstOrNull()
            ?: throw RegistryException("git-agent (repo $repo) must specify one of branch, tag, or rev")

        val safeRepoPath = Path.of(safeRepoName, idType, encoder.encodeToString(idValue.toByteArray()))
        val fullRepoPath = context.registryResolutionContext.config.cache.agent.resolve(safeRepoPath)
        val fullAgentTomlPath = fullRepoPath.resolve(AGENT_FILE)

        if (!fullAgentTomlPath.toFile().exists()) {
            throw RegistryException("git-agent (repo $repo) does not contain a coral-agent.toml file (checked out in $fullRepoPath)")
        }

        /*
            A lockfile is really missing here.  But for now, only attempt to pull the requested Git repo if an
            attempt to pull this repo with this branch/tag/rev has not already been made.

            In the future, only pulls to be tags should be allowed, and requests made for branch or tags should be
            locked into tags after their first pull.
         */
        if (!fullRepoPath.resolve(".git").isDirectory()) {
            logger.info { "Pulling Git agent repo $repo using $idType=$idValue" }

            KGit.cloneRepository {
                setDirectory(fullRepoPath.toFile())
                setBranch(idValue)
                setCloneSubmodules(true)
                setURI(repo)
                setTimeout(60)
            }
        }
        else {
            logger.info { "Using previously checked out repo $fullRepoPath for agent repo $repo" }
        }

        try {
            return listOf(resolveRegistryAgentFromStream(
                file = fullAgentTomlPath.toFile(),
                context = context.registryResolutionContext,
                exportSettings = unresolvedExportSettings
            ))
        }
        catch (e: Exception) {
            logger.error { "Could not parse $fullAgentTomlPath provided by git repo $repo ($idType=$idValue)" }
            throw e
        }
    }
}