@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
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
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.remote.RemoteSession
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
    val registry: AgentRegistry = AgentRegistry(),
) {
    private val remoteScope = CoroutineScope(Dispatchers.IO)
    private val eventBusses: MutableMap<String, MutableMap<String, EventBus<RuntimeEvent>>> = mutableMapOf()
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    @OptIn(ExperimentalUuidApi::class)
    private val applicationRuntimeContext: ApplicationRuntimeContext = ApplicationRuntimeContext(config)

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
            agentName = agentName,
            applicationId = applicationId,
            privacyKey = privacyKey,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
        )

        val agent = registry.importedAgents[graphAgent.name]
            ?: throw IllegalArgumentException("Cannot spawn unknown agent: ${graphAgent.name}")

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Local -> {
                val runtime = agent.runtimes.getById(provider.runtime) ?:
                    throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(runtime.spawn(
                    params,
                    getBusOrCreate(params.session.id, params.agentName),
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

                        // todo: support https somehow?
                        val url = URLBuilder(
                            protocol = URLProtocol.HTTP,
                            host = server.address,
                            port = server.port.toInt(),
                            pathSegments = listOf("api", "v1", "agents", "claim"),
                        ).build()

                        val client = HttpClient(CIO) {
                            install(ContentNegotiation) {
                                json(apiJsonConfig)
                            }
                        }
                        val response = client.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(GraphAgentRequest(
                                registryAgentName = graphAgent.name,
                                agentName = graphAgent.name,
                                options = graphAgent.options,
                                systemPrompt = graphAgent.systemPrompt,
                                blocking = graphAgent.blocking,
                                tools = graphAgent.extraTools,
                                provider = GraphAgentProvider.Local(provider.runtime)
                            ))
                        }
                        if (response.status != HttpStatusCode.OK) {
                            val body = response.bodyAsText()
                            logger.warn {"Failed to connect to ${server.address} (${response.status}): $body" }
                            continue
                        }

                        val runtime = RemoteRuntime(server, response.bodyAsText())
                        handles.add(runtime.spawn(
                            params,
                            getBusOrCreate(params.session.id, params.agentName),
                            applicationRuntimeContext
                        ))
                    }
                }
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
        session: RemoteSession,
        graphAgent: GraphAgent,
        agentName: String
    ) {
        val params = RuntimeParams.Remote(
            session = session,
            agentName = agentName,
            systemPrompt = graphAgent.systemPrompt,
            options = graphAgent.options,
        )

        val agent = registry.importedAgents[graphAgent.name]
            ?: throw IllegalArgumentException("Cannot spawn unknown agent: ${graphAgent.name}")

        when (val provider = graphAgent.provider) {
            is GraphAgentProvider.Remote -> throw IllegalArgumentException("Remote agents cannot be provided by other remote servers")
            is GraphAgentProvider.Local -> {
                val runtime = agent.runtimes.getById(provider.runtime) ?:
                    throw IllegalArgumentException("The requested runtime: ${provider.runtime} is not supported on agent ${graphAgent.name}")

                handles.add(runtime.spawn(
                    params,
                    getBusOrCreate(params.session.id, params.agentName),
                    applicationRuntimeContext)
                )
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        remoteScope.cancel()
        handles.map { async { it.destroy() } }.awaitAll()
    }
}
