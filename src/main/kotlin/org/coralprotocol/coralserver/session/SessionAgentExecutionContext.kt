package org.coralprotocol.coralserver.session

import io.ktor.utils.io.*
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.*
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.events.SessionEvent
import java.io.File

class SessionAgentExecutionContext(
    val agent: SessionAgent,
    val applicationRuntimeContext: ApplicationRuntimeContext
) {

    val logger = agent.logger
    val name = agent.name
    val session = agent.session

    val graphAgent = agent.graphAgent
    val options = graphAgent.options
    val provider = graphAgent.provider

    val registryAgent = graphAgent.registryAgent
    val runtimes = registryAgent.runtimes
    val path = registryAgent.path

    val disposableResources = mutableListOf<SessionAgentDisposableResource>()

    /**
     * Builds the required environment variables for the execution of this agent.
     *
     * This function will create one temporary file for each option in [options] that
     * uses [AgentOptionTransport.FILE_SYSTEM].  The temporary file will be wrapped as
     * a [SessionAgentDisposableResource.TemporaryFile] that will be put into [disposableResources]. Clean up for these
     * temporary files is therefore handled by [handleRuntimeStopped]
     *
     * If the [provider] uses a [RuntimeId.DOCKER] runtime, the temporary files path will be translated by
     */
    fun buildEnvironment(): Map<String, String> {
        return buildMap {
            val runtime = when (provider) {
                is GraphAgentProvider.Local -> provider.runtime
                is GraphAgentProvider.Remote -> provider.runtime
                is GraphAgentProvider.RemoteRequest -> provider.runtime
            }

            val addressConsumer = when (runtime) {
                RuntimeId.EXECUTABLE -> AddressConsumer.LOCAL
                RuntimeId.DOCKER -> AddressConsumer.CONTAINER
                RuntimeId.FUNCTION -> AddressConsumer.LOCAL
            }

            val isContainer = runtime == RuntimeId.DOCKER

            val filePathSeparator = if (isContainer) {
                applicationRuntimeContext.config.dockerConfig.containerPathSeparator
            }
            else {
                File.pathSeparatorChar
            }.toString()

            // User options
            options.forEach { (name, value) ->
                when (value.option().transport) {
                    AgentOptionTransport.ENVIRONMENT_VARIABLE -> {
                        this[name] = value.asEnvVarValue()
                    }
                    AgentOptionTransport.FILE_SYSTEM -> {
                        val resources = value.asFileSystemValue(applicationRuntimeContext.config.dockerConfig)
                        disposableResources.addAll(resources)

                        this[name] = resources.joinToString(filePathSeparator) {
                            if (isContainer) {
                                it.mountPath
                            }
                            else {
                                it.file.toString()
                            }
                        }
                    }
                }

                logger.info("Setting option \"$name\" = \"${this[name]}\" for agent $name")
            }

            // Coral environment variables
            this["CORAL_CONNECTION_URL"] = applicationRuntimeContext.getMcpUrl(this@SessionAgentExecutionContext, addressConsumer).toString()
            this["CORAL_AGENT_ID"] = agent.name
            this["CORAL_AGENT_SECRET"] = agent.secret
            this["CORAL_SESSION_ID"] = agent.session.id
            this["CORAL_API_URL"] = applicationRuntimeContext.getApiUrl(addressConsumer).toString()
            this["CORAL_SEND_CLAIMS"] = "0"
            this["CORAL_RUNTIME_ID"] = runtime.toString().lowercase()

            if (agent.graphAgent.systemPrompt != null)
                this["CORAL_PROMPT_SYSTEM"] = agent.graphAgent.systemPrompt

            if (agent.graphAgent.provider is GraphAgentProvider.Remote)
                this["CORAL_REMOTE_AGENT"] = "1"
        }
    }

    /**
     * Routing function to call [executeLocal] or [executeRemote]
     */
    suspend fun launch() {
        if (provider is GraphAgentProvider.RemoteRequest)
            throw IllegalArgumentException("SessionAgent tried to execute an unresolved RemoteRequest")

        try {
            handleRuntimeStarted()
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
        }
        finally {
            handleRuntimeStopped()
        }

        // todo: restart logic

        agent.logger.info("Agent ${agent.name} runtime exited")
    }


    /**
     * Execution logic for [GraphAgentProvider.Local]
     */
    suspend fun launchLocal(provider: GraphAgentProvider.Local) {
        val runtime = runtimes.getById(provider.runtime)
            ?: throw java.lang.IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported}")

        runtime.execute(this@SessionAgentExecutionContext, applicationRuntimeContext)
    }

    /**
     * Execution logic for [GraphAgentProvider.Remote]
     */
    suspend fun launchRemote(provider: GraphAgentProvider.Remote) {
        TODO()
    }

    /**
     * Called immediately before the runtime starts.
     */
    private fun handleRuntimeStarted() {
        session.events.tryEmit(SessionEvent.RuntimeStarted(name))
    }

    /**
     * Called immediately after the runtime stops, for any reason.
     */
    private fun handleRuntimeStopped() {
        session.events.tryEmit(SessionEvent.RuntimeStopped(name))
        disposableResources.forEach { it.dispose() }
        disposableResources.clear()
    }
}