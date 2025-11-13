@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.send
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.apache.commons.io.file.PathUtils.deleteFile
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.option.value
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.Session
import org.coralprotocol.coralserver.session.SessionCloseMode
import org.coralprotocol.coralserver.session.remote.RemoteSession
import java.nio.file.Path
import kotlin.collections.getOrPut
import kotlin.system.measureTimeMillis
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

enum class LogKind {
    STDOUT,
    STDERR,
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface RuntimeEvent {
    @Serializable
    @SerialName("log")
    data class Log(
        val timestamp: Long = System.currentTimeMillis(),
        val kind: LogKind,
        val message: String
    ) : RuntimeEvent

    @Serializable
    @SerialName("stopped")
    data class Stopped(val timestamp: Long = System.currentTimeMillis()) : RuntimeEvent
}

suspend fun  WebSocketServerSession.sendRuntimeEvent(event: RuntimeEvent): Unit =
    send(apiJsonConfig.encodeToString(RuntimeEvent.serializer(), event))

interface Orchestrate {
    fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle
}

abstract class OrchestratorHandle(
    val temporaryFiles: MutableList<Path> = mutableListOf()
) {
    val scope = CoroutineScope(Dispatchers.IO)
    protected abstract suspend fun cleanup()

    fun deleteTemporaryFiles() {
        temporaryFiles.forEach {
            try {
                deleteFile(it)
            }
            catch (e: IOException) {
                logger.error(e) { "Failed to delete temporary file $it" }
            }
        }
        temporaryFiles.clear()
    }

    suspend fun destroy() {
        deleteTemporaryFiles()
        cleanup()
    }
}

class Orchestrator(
    val config: Config = Config(),
    val registry: AgentRegistry = AgentRegistry()
) {
    private val remoteScope = CoroutineScope(Dispatchers.IO)
    private val eventBusses: MutableMap<String, MutableMap<String, EventBus<RuntimeEvent>>> = mutableMapOf()
    private val handles: MutableMap<String, MutableList<OrchestratorHandle>> = mutableMapOf()

    @OptIn(ExperimentalUuidApi::class)
    private val applicationRuntimeContext: ApplicationRuntimeContext = ApplicationRuntimeContext(config)

    init {
        registry.agents
            .filter { it.runtimes.dockerRuntime != null }
            .forEach {
                val image = it.runtimes.dockerRuntime!!.image
                try {
                    val time = measureTimeMillis {
                        applicationRuntimeContext.dockerClient.pullImageCmd(image)
                    }
                    logger.info { "Preloaded agent ${it.info.identifier}'s docker image $image in ${time}ms" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to pull agent ${it.info.identifier}'s docker image $image" }
                    logger.warn { "The Docker runtime will not be available for ${it.info.identifier}" }
                }
            }
    }

    fun getBus(sessionId: String, agentId: String): EventBus<RuntimeEvent>? = eventBusses[sessionId]?.get(agentId)

    private fun createAgentBus(sessionId: String, agentName: String): EventBus<RuntimeEvent> {
        val eventBus = EventBus<RuntimeEvent>(replay = 512)
        val sessionBusses = eventBusses.getOrPut(sessionId) { mutableMapOf() }
        sessionBusses[agentName] = eventBus

        return eventBus
    }

    private fun registerHandle(
        agent: GraphAgent,
        session: Session,
        handle: OrchestratorHandle,
        bus: EventBus<RuntimeEvent>
    ) {
        val sessionHandles = handles.getOrPut(session.id) { mutableListOf() }
        sessionHandles.add(handle)

        bus.events.onEach {
            if (it is RuntimeEvent.Stopped) {
                // Note this must be done BEFORE any session destruction - destroying sessions have a chance of making
                // this event omit again
                sessionHandles.remove(handle)

                // If the runtime crashes, this is the only place we can call this cleanup function
                handle.deleteTemporaryFiles()

                logger.info { "Received the stop runtime event for agent '${agent.name}'" }

                when (session) {
                    is RemoteSession -> {
                        /*
                            It is very safe to clean up remote sessions like this because a remote session will only
                            ever contain one agent.  When that one agent dies, there is no longer a point for that
                            session to ever exist again.
                         */
                        session.destroy(SessionCloseMode.CLEAN)
                    }
                    is LocalSession -> {
                        //TODO: a lot of potential here for better lifecycle management and coupling configuration
                        // between the respective session and agent lifecycles
                        if (agent.blocking == true) {
                            session.destroy(SessionCloseMode.CLEAN)
                        }
                    }
                }
            }
        }.launchIn(handle.scope)
    }

    private fun executeRuntime(
        params: RuntimeParams,
        runtime: Orchestrate,
        graphAgent: GraphAgent,
        session: Session
    ) {
        val bus = createAgentBus(params.getId(), params.agentName)
        val handle = runtime.spawn(params, bus, applicationRuntimeContext)
        registerHandle(graphAgent, session, handle, bus)
    }

    fun spawn(
        session: LocalSession,
        graphAgent: GraphAgent,
        agentName: String,
        applicationId: String,
        privacyKey: String
    ) {
        val params = RuntimeParams.Local(
            session = session,
            agentId = graphAgent.registryAgent.info.identifier,
            agentName = agentName,
            applicationId = applicationId,
            privacyKey = privacyKey,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
            path = graphAgent.registryAgent.path
        )

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Local -> {
                val runtime = graphAgent.registryAgent.runtimes.getById(provider.runtime)
                    ?: throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                executeRuntime(params, runtime, graphAgent, session)
            }

            is GraphAgentProvider.Remote -> remoteScope.launch {
                val request = PaidGraphAgentRequest(
                    GraphAgentRequest(
                        id = graphAgent.registryAgent.info.identifier,
                        name = graphAgent.name,
                        description = graphAgent.description,
                        options = graphAgent.options.mapValues { it.value.value() },
                        systemPrompt = graphAgent.systemPrompt,
                        blocking = graphAgent.blocking,
                        customToolAccess = graphAgent.customToolAccess,
                        plugins = graphAgent.plugins,
                        provider = GraphAgentProvider.Local(provider.runtime),
                    ),
                    clientWalletAddress = config.paymentConfig.wallet?.address
                        ?: throw IllegalStateException("Requests for remote agents cannot be made without a configured wallet"),
                    paidSessionId = session.paymentSessionId
                        ?: throw IllegalStateException("Session including paid agents does not include a payment session")
                )

                val runtime = RemoteRuntime(provider.server, provider.server.createClaim(request))
                executeRuntime(params, runtime, graphAgent, session)
            }

            is GraphAgentProvider.RemoteRequest -> throw IllegalArgumentException("Remote requests must be resolved before orchestration")
        }
    }

    /**
     * Remote agent function!
     *
     * This function should be called on the server that exports agents to spawn an agent that
     * was requested by another server.
     */
    fun spawnRemote(
        session: RemoteSession,
        graphAgent: GraphAgent,
        agentName: String
    ) {
        val params = RuntimeParams.Remote(
            session = session,
            agentId = graphAgent.registryAgent.info.identifier,
            agentName = agentName,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
            path = graphAgent.registryAgent.path
        )

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.RemoteRequest, is GraphAgentProvider.Remote -> {
                throw IllegalArgumentException("Remote agents cannot be provided by other remote servers")
            }

            is GraphAgentProvider.Local -> {
                val runtime = graphAgent.registryAgent.runtimes.getById(provider.runtime)
                    ?: throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.registryAgent.info.identifier}")

                executeRuntime(params, runtime, graphAgent, session)
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        remoteScope.cancel()
        handles.values.flatten().map {
            async {
                try {
                    it.destroy()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to destroy runtime" }
                }
            }
        }.awaitAll()
    }

    suspend fun killForSession(sessionId: String, sessionCloseMode: SessionCloseMode): Unit = coroutineScope {
        handles[sessionId]?.map {
            async {
                try {
                    it.destroy()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to destroy runtime for session $sessionId" }
                }
            }
        }?.awaitAll()
    }
}
