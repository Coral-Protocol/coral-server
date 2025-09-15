package org.coralprotocol.coralserver.agent.registry.indexer

import com.github.syari.kgit.KGit
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.config.Config
import org.eclipse.jgit.api.ResetCommand
import java.nio.file.Path
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

@Serializable
data class GitRegistryAgentIndexer(
    val url: String,
    override val priority: Int
) : RegistryAgentIndexer {
    private fun indexerPath(cachePath: Path, indexerName: String) =
        cachePath.resolve(Path.of(indexerName))

    override fun resolveAgent(
        context: RegistryResolutionContext,
        exportSettings: UnresolvedAgentExportSettingsMap,
        indexerName: String,
        agentName: String,
        version: String
    ): RegistryAgent {
        val path = indexerPath(context.config.cache.index, indexerName)

        val agentTomlFile = path.resolve(Path.of(version, agentName, AGENT_FILE))
        if (!agentTomlFile.toFile().exists()) {
            throw RegistryException("Indexer $indexerName does not contain agent $agentName:$version")
        }

        try {
            return resolveRegistryAgentFromStream(
                file = agentTomlFile.toFile(),
                context = context,
                exportSettings = exportSettings
            )
        }
        catch (e: Exception) {
            logger.error { "Could not parse agent $agentName provided by indexer $indexerName ($agentTomlFile)" }
            throw e
        }
    }

    override fun update(config: Config, indexerName: String) {
        val path = indexerPath(config.cache.index, indexerName)

        try {
            val repo = if (!path.resolve(".git").isDirectory()) {
                KGit.cloneRepository {
                    setDirectory(path.toFile())
                    setCloneSubmodules(true)
                    setURI(url)
                    setTimeout(60)
                }
            }
            else {
                KGit.open(path.toFile())
            }

            // todo: lockfile, caching, etc
            repo.fetch()
            repo.pull()
            repo.reset {
                setMode(ResetCommand.ResetType.HARD)
            }
            repo.submoduleUpdate {
                setFetch(true)
            }
        }
        catch (e: Exception) {
            throw RegistryException("Error while updating indexer $indexerName: $e")
        }
    }
}