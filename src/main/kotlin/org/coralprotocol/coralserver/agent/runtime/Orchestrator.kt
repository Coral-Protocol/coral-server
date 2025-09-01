@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentServer
import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.remote.RemoteGraphAgentRequest
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.models.agent.ClaimAgentsModel
import org.coralprotocol.coralserver.session.remote.createRemoteSessionClient
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
    val app: ConfigCollection = ConfigCollection(null)
) {
    private val remoteScope = CoroutineScope(Dispatchers.IO)
    private val eventBusses: MutableMap<String, MutableMap<String, EventBus<RuntimeEvent>>> = mutableMapOf()
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    @OptIn(ExperimentalUuidApi::class)
    private val applicationRuntimeContext: ApplicationRuntimeContext = ApplicationRuntimeContext(app)

    fun getBus(sessionId: String, agentId: String): EventBus<RuntimeEvent>? = eventBusses[sessionId]?.get(agentId)

    private fun getBusOrCreate(sessionId: String, agentId: String) = eventBusses.getOrPut(sessionId) {
        mutableMapOf()
    }.getOrPut(agentId) {
        EventBus(replay = 512)
    }

    fun spawn(
        sessionId: String,
        graphAgent: GraphAgent,
        agentName: String,
        applicationId: String,
        privacyKey: String
    ) {
        val params = RuntimeParams.Local(
            sessionId = sessionId,
            agentName = agentName,
            applicationId = applicationId,
            privacyKey = privacyKey,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
        )

        val agent = app.registry.importedAgents[graphAgent.name]
            ?: throw IllegalArgumentException("Cannot spawn unknown agent: ${graphAgent.name}")

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Local -> {
                val runtime = agent.runtimes.getById(provider.runtime) ?:
                    throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(runtime.spawn(
                    params,
                    getBusOrCreate(params.sessionId, params.agentName),
                    applicationRuntimeContext)
                )
            }

            is GraphAgentProvider.Remote -> {
                val rankedServers = when (provider.serverSource) {
                    is GraphAgentServerSource.Servers -> {
                        provider.serverSource.servers.sortedBy {
                            provider.serverScoring?.getScore(it) ?: 1.0
                        }
                    }

                    is GraphAgentServerSource.Indexer -> TODO("indexer server source not yet supported")
                }


                /*
                    Workflow:
                    1. Iterate over ranked servers (maintaining order!), finding the first that responds to "pings"
                    2. Request the agent from the server.  If they decline, move to the next server
                    3. If they accept:
                        a. Do payment stuff, if this fails, move to the next server
                        b. Open WebSocket connection with the server, this WebSocket connection can be treated like a
                           bus for a process or docker container
                        c. Tie the life-cycle of an agent to the WebSocket connection and vice versa, again similarly to a
                           process or container
                        d. More payment stuff?
                    4. If the list of servers is exhausted without having found a suitable server to provide the agent,
                       an exception should be thrown
                 */

                // do the request
                remoteScope.launch {
                    for (server in rankedServers) {
                        val client = HttpClient(CIO) {
                            install(ContentNegotiation) {
                                json()
                            }
                        }
                        val response = client.post(server.address) {
                            url {
                                appendPathSegments("api", "v1", "agents", "claim")
                            }
                            contentType(ContentType.Application.Json)
                            setBody(
                                ClaimAgentsModel(
                                    agents = listOf(
                                        RemoteGraphAgentRequest(
                                            name = agentName,
                                            type = graphAgent.name,
                                            options = graphAgent.options,
                                            systemPrompt = graphAgent.systemPrompt,
                                            extraTools = graphAgent.extraTools,
                                            runtime = provider.runtime
                                        )
                                    )
                                )
                            )
                        }
                        if (response.status != HttpStatusCode.OK) {
                            val body = response.bodyAsText()
                            logger.warn {"Failed to connect to ${server.address} (${response.status}): $body" }
                            continue
                        }
                        logger.info { response }


                        // websocket id
                        // connect
                        // exit when websocket die
                        establishConnection(server, sessionId)
                    }
                }

                //remoteAgents.set(returnedKey, CoralAgentIndividualMcp())
            }
        }
    }

    /**
     * Remote agent function!
     *
     * This function should be called on the server that exports agents to spawn an agent that
     * was requested by another server.
     */
    fun spawnRemote(
        remoteSessionId: String,
        graphAgent: GraphAgent,
        agentName: String
    ) {
        val params = RuntimeParams.Remote(
            remoteSessionId = remoteSessionId,
            agentName = agentName,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
        )

        val agent = app.registry.importedAgents[graphAgent.name]
            ?: throw IllegalArgumentException("Cannot spawn unknown agent: ${graphAgent.name}")

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Remote -> throw IllegalArgumentException("Remote agents cannot be provided by other remote servers")
            is GraphAgentProvider.Local -> {
                val runtime = agent.runtimes.getById(provider.runtime) ?:
                    throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(runtime.spawn(
                    params,
                    getBusOrCreate(params.remoteSessionId, params.agentName),
                    applicationRuntimeContext)
                )
            }
        }
    }

    /**
     * Remote agent function!
     *
     * This function should be called by an importing server to establish a connection to an agent that is running on
     * an external server.  A WebSocket connection to an agent running on an external server has a lot of similarities
     * between to a local agent's Runtime.
     */
    fun establishConnection(
        server: GraphAgentServer,
        claimId: String,
    ) {
        val webSocketClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
        }

        runBlocking {
            webSocketClient.webSocket(
                host = server.address,
                port = server.port.toInt(),
                path = "/ws/v1/exported/$claimId",
            ) {
                createRemoteSessionClient()
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        remoteScope.cancel()
        handles.map { async { it.destroy() } }.awaitAll()
    }
}
