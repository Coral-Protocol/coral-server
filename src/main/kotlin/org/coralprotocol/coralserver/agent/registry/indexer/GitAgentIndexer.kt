package org.coralprotocol.coralserver.agent.registry.indexer

import com.akuleshov7.ktoml.source.decodeFromStream
import com.github.syari.kgit.KGit
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.reference.AGENT_FILE
import org.coralprotocol.coralserver.config.Config
import org.eclipse.jgit.api.ResetCommand
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

@Serializable
data class GitAgentIndexer(
    val url: String,
    override val priority: Int
) : AgentIndexer {
    private fun indexerPath(cachePath: Path, indexerName: String) =
        cachePath.resolve(Path.of(indexerName))

    override fun resolveAgent(
        context: RegistryResolutionContext,
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
            val agent = context.serializer.decodeFromStream<UnresolvedRegistryAgent>(agentTomlFile.inputStream())
            return agent.resolve()
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