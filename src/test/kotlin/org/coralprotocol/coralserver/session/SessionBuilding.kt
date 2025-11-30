package org.coralprotocol.coralserver.session

import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentInfo
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.nio.file.Path

open class SessionBuilding {
    protected val sessionManager = LocalSessionManager(
        // no blockchain services in testing
        blockchainService = null,

        // port for testing is zero
        applicationRuntimeContext = ApplicationRuntimeContext(
            config = Config(
                networkConfig = NetworkConfig(
                    bindPort = 0u
                )
            )
        ),

        // might as well...
        jupiterService = JupiterService(),
    )

    protected fun registryAgent(
        name: String = "TestAgent",
        version: String = "1.0.0",
        description: String = "Test agent",
        executableRuntime: ExecutableRuntime? = null,
        functionRuntime: FunctionRuntime? = null
    ) =
        RegistryAgent(
            info = RegistryAgentInfo(
                name = name,
                version = version,
                description = description,
                capabilities = setOf()
            ),
            runtimes = LocalAgentRuntimes(
                executableRuntime = executableRuntime,
                functionRuntime = functionRuntime
            ),
            path = Path.of(System.getProperty("user.dir")),
            unresolvedExportSettings = mapOf(),
            options = mapOf()
        )

    protected fun graphAgent(
        registryAgent: RegistryAgent = registryAgent(),
        blocking: Boolean = true,
        provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
        options: Map<String, AgentOptionWithValue> = mapOf()
    ) =
        GraphAgent(
            registryAgent = registryAgent,
            name = registryAgent.info.name,
            description = registryAgent.info.description,
            options = options,
            systemPrompt = null,
            blocking = blocking,
            customToolAccess = setOf(),
            plugins = setOf(),
            provider = provider,
            x402Budgets = listOf(),
        )

    protected fun graphAgent(
        name: String,
        blocking: Boolean = true,
        provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
    ) =
        graphAgent(
            registryAgent = registryAgent(name = name),
            blocking = blocking,
            provider = provider
        )

    protected fun sseEnv(body: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                install(io.ktor.server.resources.Resources)
                routing { mcpRoutes(sessionManager) }
                install(ContentNegotiation) {
                    json(apiJsonConfig, contentType = ContentType.Application.Json)
                }
            }

            client = createClient {
                install(Resources)
                install(SSE)
            }

            body()
        }
    }
}