package org.coralprotocol.coralserver

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.orchestrator.AgentRegistry
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.Orchestrator
import org.coralprotocol.coralserver.orchestrator.RegistryAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.orchestrator.runtime.Executable
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.AgentGraph
import org.coralprotocol.coralserver.session.AgentGraphRequest
import org.coralprotocol.coralserver.session.AgentName
import org.coralprotocol.coralserver.session.CreateSessionRequest
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.session.GraphAgentRequest
import org.coralprotocol.coralserver.session.SessionManager

class GaiaApplication {
    val searchAgent = AgentType("search")
    val planningAgent = AgentType("planning")
    val serverPort: UShort = 12080u

    val registry = AgentRegistry(
        mapOf(
            searchAgent to RegistryAgent(
                Executable(listOf("bash", "coral-GAIA/venv.sh", "coral-GAIA/agents/search_agent.py"), listOf()),
                optionsList = listOf()
            ),
            planningAgent to RegistryAgent(
                Executable(listOf("bash", "coral-GAIA/venv.sh", "coral-GAIA/agents/planning_agent.py"), listOf()),
                optionsList = listOf()
            )
        )
    )
    val orchestrator = Orchestrator(registry)

    //      command: ["bash", "examples/camel-search-maths/venv.sh", "examples/camel-search-maths/test.py"]
    val server = CoralServer(
        devmode = false,
        sessionManager = SessionManager(
            orchestrator,
            serverPort,
        ),
        appConfig = AppConfig(
            registry = mapOf(
                searchAgent to RegistryAgent(
                    Executable(listOf("python coral-GAIA/agents/search_agent.py"), listOf()), listOf()
                )
            )
        )
    )

    val client = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun run() {
        val address = "http://localhost:${server.port}"
        // Send a request to the server to create a session
        server.appConfig.registry
        client.post("$address/sessions") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateSessionRequest(
                    "gaia", "gaia-1", "public", AgentGraphRequest(
                        agents = hashMapOf(
                            AgentName("search") to GraphAgentRequest.Local(searchAgent),
                            AgentName("planning") to GraphAgentRequest.Local(planningAgent)
                        ),
                        links = setOf(setOf("search", "planning"))
                    )
                )
            )
        }

        delay(10000)

        println(server.host)
    }

}

suspend fun main(args: Array<String>) {
    GaiaApplication().apply {
        server.start()
        run()
        server.stop()
    }
}