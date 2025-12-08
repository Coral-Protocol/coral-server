package org.coralprotocol.coralserver.session

import DockerRuntime
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentInfo
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.nio.file.Path

open class SessionBuilding {
    protected val mcpToolManager = McpToolManager()
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
        mcpToolManager = mcpToolManager
    )

    protected fun registryAgent(
        name: String = "TestAgent",
        version: String = "1.0.0",
        description: String = "Test agent",
        executableRuntime: ExecutableRuntime? = null,
        functionRuntime: FunctionRuntime? = null,
        dockerRuntime: DockerRuntime? = null
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
                functionRuntime = functionRuntime,
                dockerRuntime = dockerRuntime
            ),
            path = Path.of(System.getProperty("user.dir")),
            unresolvedExportSettings = mapOf(),
            options = mapOf()
        )

    protected fun graphAgent(
        registryAgent: RegistryAgent = registryAgent(),
        blocking: Boolean = true,
        provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
        options: Map<String, AgentOptionWithValue> = mapOf(),
        plugins: Set<GraphAgentPlugin> = setOf(),
    ) =
        registryAgent.info.name to GraphAgent(
            registryAgent = registryAgent,
            name = registryAgent.info.name,
            description = registryAgent.info.description,
            options = options,
            systemPrompt = null,
            blocking = blocking,
            customToolAccess = setOf(),
            plugins = plugins,
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

    protected fun sseEnv(body: suspend ApplicationTestBuilder.() -> Unit) = runTest {
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