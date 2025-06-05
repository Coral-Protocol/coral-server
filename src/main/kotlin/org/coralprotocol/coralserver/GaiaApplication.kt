package org.coralprotocol.coralserver

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.orchestrator.AgentRegistry
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.ConfigEntry
import org.coralprotocol.coralserver.orchestrator.Orchestrator
import org.coralprotocol.coralserver.orchestrator.RegistryAgent
import org.coralprotocol.coralserver.orchestrator.runtime.Executable
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.*

class GaiaApplication {
    val searchAgent = AgentType("search")
    val planningAgent = AgentType("planning")

    val serverPort: UShort = 5555u
    val openAiApiKey: String = System.getenv("OPENAI_API_KEY")
    val commonRegistryEnvList = listOf(
        EnvVar(
            "OPENAI_API_KEY",
            from = "OPENAI_API_KEY",
            value = openAiApiKey,
            option = "OPENAI_API_KEY"
        )
    )
    val commonRegistryOptionsList = listOf(ConfigEntry.Str("OPENAI_API_KEY", "OpenAI API Key", null))
    val registry = AgentRegistry(
        mapOf(
            searchAgent to registerGaiaAgent("coral-GAIA/agents/search_agent.py"),
            planningAgent to registerGaiaAgent("coral-GAIA/agents/planning_agent.py"),
        )
    )

    private fun registerGaiaAgent(agentPath: String): RegistryAgent = RegistryAgent(
        Executable(
            listOf("bash", "coral-GAIA/venv.sh", agentPath),
            commonRegistryEnvList
        ),
        optionsList = commonRegistryOptionsList
    )

    val orchestrator = Orchestrator(registry)

    val server = CoralServer(
        devmode = false,
        sessionManager = SessionManager(
            orchestrator,
            serverPort,
        ),
        appConfig = AppConfig(
            registry = registry.agents
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
                creationSessionRequest()
            )
        }

        delay(100000)
        println(server.host)
    }

    private fun creationSessionRequest(): CreateSessionRequest {
        val commonOptions = mapOf("OPENAI_API_KEY" to JsonPrimitive(openAiApiKey))
        return CreateSessionRequest(
            "gaia", "gaia-1", "public", AgentGraphRequest(
                agents = hashMapOf(
                    AgentName("search") to GraphAgentRequest.Local(
                        searchAgent,
                        blocking = true,
                        options = commonOptions
                    ),
                    AgentName("planning") to GraphAgentRequest.Local(
                        planningAgent,
                        blocking = true,
                        options = commonOptions
                    )
                ),
                links = setOf(setOf("search", "planning"))
            )
        )
    }
}

suspend fun main(args: Array<String>) {
    GaiaApplication().apply {
        server.start()
        run()
        server.stop()
    }
}