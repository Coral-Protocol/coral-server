package org.coralprotocol.coralserver.session

import io.ktor.http.buildUrl
import io.ktor.utils.io.CancellationException
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.asEnvVarValue
import org.coralprotocol.coralserver.agent.registry.option.asFileSystemValue
import org.coralprotocol.coralserver.agent.registry.option.option
import org.coralprotocol.coralserver.agent.registry.option.toDisplayString
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import java.io.File
import java.nio.file.Path
import kotlin.collections.forEach

class SessionAgentExecutionContext(
    val agent: SessionAgent,
    val applicationRuntimeContext: ApplicationRuntimeContext
) {
    val logger = agent.logger
    val name = agent.name
    val options = agent.graphAgent.options
    val provider = agent.graphAgent.provider
    val runtimes = agent.graphAgent.registryAgent.runtimes
    val path = agent.graphAgent.registryAgent.path
    val sseUrl = buildUrl {

    }

    // todo: delete when agent dies
    var tempFiles = mutableListOf<Path>()

    /**
     * Environment variables to be set for this agent.  This includes all environment variables Coral sets for an agent
     * (e.g., variables for their connection to the MCP server) as well as all options generated from options set for
     * this agent.
     */
    val environment: Map<String, String> = buildMap {
        options.forEach { (name, value) ->
            when (value.option().transport) {
                AgentOptionTransport.ENVIRONMENT_VARIABLE -> {
                    this[name] = value.asEnvVarValue()
                    logger.info("Setting option \"$name\" = \"${value.toDisplayString()}\" for agent $name")
                }
                AgentOptionTransport.FILE_SYSTEM -> {
                    val files = value.asFileSystemValue()
                    val env = files.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
                    this[name] = env
                    tempFiles.addAll(files)

                    logger.info("Setting option \"$name\" = \"$env\" for agent $name")
                }
            }
        }

        // todo: coral env vars
    }

    /**
     * Routing function to call [executeLocal] or [executeRemote]
     */
    suspend fun launch() {
        if (provider is GraphAgentProvider.RemoteRequest)
            throw IllegalArgumentException("SessionAgent tried to execute an unresolved RemoteRequest")

        try {
            if (provider is GraphAgentProvider.Local)
                launchLocal(provider)

            if (provider is GraphAgentProvider.Remote)
                launchRemote(provider)
        }
        catch (_: CancellationException) {
            agent.logger.info("Agent ${agent.name} cancelled")
        }
        catch (e: Exception) {
            agent.logger.error("Exception thrown when launching agent ${agent.name}", e)
            // todo: restart logic
        }

        agent.logger.info("Agent ${agent.name} runtime exited")
    }


    /**
     * Execution logic for [GraphAgentProvider.Local]
     */
    suspend fun launchLocal(provider: GraphAgentProvider.Local) {
        val runtime = runtimes.getById(provider.runtime)!!
        return runtime.execute(this@SessionAgentExecutionContext, applicationRuntimeContext)
    }

    /**
     * Execution logic for [GraphAgentProvider.Remote]
     */
    suspend fun launchRemote(provider: GraphAgentProvider.Remote) {
        TODO()
    }
}