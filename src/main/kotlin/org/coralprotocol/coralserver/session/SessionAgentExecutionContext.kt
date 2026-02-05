@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.*
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.DebugConfig
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.session.reporting.SessionAgentUsageReport
import org.coralprotocol.coralserver.util.utcTimeNow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SessionAgentExecutionContext(
    val agent: SessionAgent,
    val applicationRuntimeContext: ApplicationRuntimeContext
) : KoinComponent {
    private companion object {
        private const val DEV_ENV_FILE_NAME = "coral-agent.dev.env"
        private const val GITIGNORE_FILE_NAME = ".gitignore"
    }

    val logger = agent.logger
    val name = agent.name
    val session = agent.session

    val graphAgent = agent.graphAgent
    val options = graphAgent.options
    val provider = graphAgent.provider

    val registryAgent = graphAgent.registryAgent
    val runtimes = registryAgent.runtimes
    val path = registryAgent.path

    val debugConfig by inject<DebugConfig>()
    val dockerConfig by inject<DockerConfig>()

    val disposableResources = mutableListOf<SessionAgentDisposableResource>()

    var lastLaunchTime: Instant? = null

    /**
     * A list of usage reports for this agent.  When a session ends, all usage reports for each agent will be sent to
     * webhooks, if configured.
     */
    val usageReports = mutableListOf<SessionAgentUsageReport>()

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
            val addressConsumer = when (provider.runtime) {
                RuntimeId.EXECUTABLE -> AddressConsumer.LOCAL
                RuntimeId.DOCKER -> AddressConsumer.CONTAINER
                RuntimeId.FUNCTION -> AddressConsumer.LOCAL
            }

            val isContainer = provider.runtime == RuntimeId.DOCKER

            val filePathSeparator = if (isContainer) {
                dockerConfig.containerPathSeparator
            } else {
                File.pathSeparatorChar
            }.toString()

            if (provider.runtime == RuntimeId.EXECUTABLE) {
                putAll(debugConfig.additionalExecutableEnvironment)
            } else if (provider.runtime == RuntimeId.DOCKER) {
                putAll(debugConfig.additionalDockerEnvironment)
            }

            // User options
            options.forEach { (name, value) ->
                when (value.option().transport) {
                    AgentOptionTransport.ENVIRONMENT_VARIABLE -> {
                        this[name] = value.asEnvVarValue()
                    }

                    AgentOptionTransport.FILE_SYSTEM -> {
                        val resources = value.asFileSystemValue(dockerConfig)
                        disposableResources.addAll(resources)

                        this[name] = resources.joinToString(filePathSeparator) {
                            if (isContainer) {
                                it.mountPath
                            } else {
                                it.file.toString()
                            }
                        }
                    }
                }

                logger.info { "Setting option \"$name\" = \"${value.toDisplayString()}\" for agent $name" }
            }

            // Coral environment variables
            this["CORAL_CONNECTION_URL"] =
                applicationRuntimeContext.getSseUrl(this@SessionAgentExecutionContext, addressConsumer).toString()
            this["CORAL_AGENT_ID"] = agent.name
            this["CORAL_AGENT_SECRET"] = agent.secret
            this["CORAL_SESSION_ID"] = agent.session.id
            this["CORAL_API_URL"] = applicationRuntimeContext.getApiUrl(addressConsumer).toString()
            this["CORAL_SEND_CLAIMS"] = "0"
            this["CORAL_RUNTIME_ID"] = provider.runtime.toString().lowercase()

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

            // Export env vars to coral-agent.dev.env if appropriate and set, instead of launching the runtime for development purposes.
            if (graphAgent.exportDevEnvFile && provider is GraphAgentProvider.Local && path != null) {
                if (exportDevEnvFile(path)) {
                    awaitCancellation() // Keep session alive as runtime wasn't launched
                }
            }

            if (provider is GraphAgentProvider.Local)
                launchLocal(provider)

            if (provider is GraphAgentProvider.Remote)
                launchRemote(provider)
        } catch (_: CancellationException) {
            logger.info { "Agent ${agent.name} cancelled" }
        } catch (e: Exception) {
            logger.error(e) { "Exception thrown when launching agent ${agent.name}" }
        } finally {
            handleRuntimeStopped()
        }

        // todo: restart logic
        logger.info { "Agent ${agent.name} runtime exited" }
    }

    private fun exportDevEnvFile(agentDirectory: Path): Boolean {
        val gitignore = agentDirectory.resolve(GITIGNORE_FILE_NAME)
        val isAllowed = gitignore.exists() && gitignore.readLines().any {
            Regex("""^\s*/?coral-agent\.dev\.env\s*$""").matches(it)
        }

        if (!isAllowed) {
            logger.warn {
                "Dev env export is enabled for agent ${agent.name}, but it did not create $DEV_ENV_FILE_NAME " +
                    "because an adjacent $GITIGNORE_FILE_NAME did not contain a line matching $DEV_ENV_FILE_NAME"
            }
            return false
        }

        val envFile = agentDirectory.resolve(DEV_ENV_FILE_NAME)
        logger.info { "Exporting $DEV_ENV_FILE_NAME for agent ${agent.name}" }
        val env = buildEnvironment()
            .toSortedMap()
            .entries
            .joinToString("\n", postfix = "\n") { (key, value) ->
                "$key=${value.toDotEnvValue()}"
            }

        envFile.writeText(env)
        disposableResources.add(SessionAgentDisposableResource.DeleteFile(envFile))

        logger.info {
            "Wrote dev env file for agent ${agent.name} to $envFile (will be deleted when the session ends)"
        }
        return true
    }

    private fun String.toDotEnvValue(): String {
        if (isEmpty()) return "\"\""

        val needsQuotes = any { it.isWhitespace() } || any { it == '#' || it == '"' || it == '\\' } || contains('\n') || contains('\r')
        if (!needsQuotes) return this

        val escaped = buildString {
            for (c in this@toDotEnvValue) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

        return "\"$escaped\""
    }


    /**
     * Execution logic for [GraphAgentProvider.Local]
     */
    suspend fun launchLocal(provider: GraphAgentProvider.Local) {
        val runtime = runtimes.getById(provider.runtime)
            ?: throw java.lang.IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported")

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
    private suspend fun handleRuntimeStarted() {
        lastLaunchTime = utcTimeNow()
        session.events.emit((SessionEvent.RuntimeStarted(name)))
    }

    /**
     * Called immediately after the runtime stops, for any reason.
     */
    private suspend fun handleRuntimeStopped() {
        val startTime = lastLaunchTime
        if (startTime != null) {
            usageReports.add(
                SessionAgentUsageReport(
                    name,
                    registryAgent.identifier,
                    startTime,
                    utcTimeNow()
                )
            )
        }

        session.events.emit(SessionEvent.RuntimeStopped(name))
        disposableResources.forEach { it.dispose() }
        disposableResources.clear()
    }
}