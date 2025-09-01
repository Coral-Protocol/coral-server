@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import com.chrynan.uri.core.Uri
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.content
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.remote.RemoteGraphAgentRequest
import org.coralprotocol.coralserver.models.agent.ClaimAgentsModel
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
        sessionManager: SessionManager?,
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val app: ConfigCollection = ConfigCollection(null),
    val port: UShort,
) {
    private val remoteScope = CoroutineScope(Dispatchers.IO)
    private val eventBusses: MutableMap<String, MutableMap<String, EventBus<RuntimeEvent>>> = mutableMapOf()
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    @OptIn(ExperimentalUuidApi::class)
    private val remoteAgents: MutableMap<Uuid, CoralAgentIndividualMcp> = mutableMapOf()

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
        port: UShort,
        relativeMcpServerUri: Uri,
        sessionManager: SessionManager?
    ) {
        val params = RuntimeParams(
            sessionId = sessionId,
            agentName = agentName,
            mcpServerPort = port,
            mcpServerRelativeUri = relativeMcpServerUri,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options
        )

        val agent = app.registry.importedAgents[graphAgent.name]
            ?: throw IllegalArgumentException("Cannot spawn unknown agent: ${graphAgent.name}")

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Local -> {
                val runtime = agent.runtimes.getById(provider.runtime)
                    ?: throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(
                    runtime.spawn(
                        params,
                        getBusOrCreate(params.sessionId, params.agentName),
                        sessionManager
                    )
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
                            setBody(ClaimAgentsModel(agents = listOf(RemoteGraphAgentRequest(
                                name = agentName,
                                type = graphAgent.name,
                                options = graphAgent.options,
                                systemPrompt = graphAgent.systemPrompt,
                                extraTools = graphAgent.extraTools,
                                runtime = provider.runtime
                            ))))
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
                    }
                }

                //remoteAgents.set(returnedKey, CoralAgentIndividualMcp())
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        remoteScope.cancel()
        handles.map { async { it.destroy() } }.awaitAll()
    }
}