@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionCloseMode
import org.coralprotocol.coralserver.session.remote.RemoteSession
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

interface Orchestrate {
    fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
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

    private fun getBusOrCreate(sessionId: String, agentId: String) = eventBusses.getOrPut(sessionId) {
        mutableMapOf()
    }.getOrPut(agentId) {
        EventBus(replay = 512)
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

        val handles = handles.getOrPut(session.id) { mutableListOf() }

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Local -> {
                val runtime = graphAgent.registryAgent.runtimes.getById(provider.runtime)
                    ?: throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(
                    runtime.spawn(
                        params,
                        getBusOrCreate(params.session.id, params.agentName),
                        applicationRuntimeContext
                    )
                )
            }

            is GraphAgentProvider.Remote -> remoteScope.launch {
                val request = PaidGraphAgentRequest(
                    GraphAgentRequest(
                        id = graphAgent.registryAgent.info.identifier,
                        name = graphAgent.name,
                        description = graphAgent.description,
                        options = graphAgent.options,
                        systemPrompt = graphAgent.systemPrompt,
                        blocking = graphAgent.blocking,
                        customToolAccess = graphAgent.customToolAccess,
                        plugins = graphAgent.plugins,
                        provider = GraphAgentProvider.Local(provider.runtime),
                    ),
                    paidSessionId = session.paymentSessionId
                        ?: throw IllegalStateException("Session including paid agents does not include a payment session")
                )

                val runtime = RemoteRuntime(provider.server, provider.server.createClaim(request))
                handles.add(
                    runtime.spawn(
                        params,
                        getBusOrCreate(params.session.id, params.agentName),
                        applicationRuntimeContext
                    )
                )
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

                handles.getOrPut(session.id) { mutableListOf() }.add(
                    runtime.spawn(
                        params,
                        getBusOrCreate(params.session.id, params.agentName),
                        applicationRuntimeContext
                    )
                )
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
