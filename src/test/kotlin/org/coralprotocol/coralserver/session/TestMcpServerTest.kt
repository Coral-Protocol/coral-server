package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestScope
import io.kotest.matchers.concurrent.suspension.shouldCompleteWithin
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeToolServerAuth
import org.coralprotocol.coralserver.utils.TestMcpServer
import org.coralprotocol.coralserver.utils.TestToolInput
import org.koin.core.component.inject
import org.koin.test.get
import kotlin.time.Duration.Companion.seconds

class TestMcpServerTest : CoralTest({

    suspend fun TestScope.testTransport(
        testServer: TestMcpServer,
        transport: StreamableHttpClientTransport
    ) {
        val json by inject<Json>()

        val mcpClient = Client(
            clientInfo = Implementation(
                name = "test",
                version = "1.0.0"
            )
        )
        mcpClient.connect(transport)

        val callToolJob = launch {
            mcpClient.callTool(
                CallToolRequest(
                    CallToolRequestParams(
                        testServer.toolName,
                        json.encodeToJsonElement(TestToolInput("test")) as JsonObject
                    )
                )
            )
        }

        shouldCompleteWithin(2.seconds) { testServer.toolChannel.receive() }
        callToolJob.join()
    }

    test("testNoAuth") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServer(get())

        testTransport(testServer, StreamableHttpClientTransport(get(), toolServer.url.resolve()))
    }

    test("testQueryParamAuth") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServerParamAuth(get())

        testTransport(
            testServer,
            StreamableHttpClientTransport(
                get(),
                toolServer.url.resolve(
                    mapOf(
                        testServer.authTokenOptionName to AgentOptionWithValue.String(
                            AgentOption.String(),
                            AgentOptionValue.String(testServer.authToken)
                        )
                    )
                )
            )
        )
    }

    test("testQueryParamAuthBadToken") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServerParamAuth(get())

        // replacement isn't performed, query param for auth token should be invalid
        shouldThrow<McpException> {
            testTransport(
                testServer,
                StreamableHttpClientTransport(
                    get(),
                    toolServer.url.resolve(
                        mapOf(
                            testServer.authTokenOptionName to AgentOptionWithValue.String(
                                AgentOption.String(),
                                AgentOptionValue.String("bad token")
                            )
                        )
                    )
                )
            )
        }
    }

    test("testBearerAuth") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServerBearerAuth(get())

        val httpClient = get<HttpClient>()

        testTransport(testServer, StreamableHttpClientTransport(httpClient.config {
            defaultRequest {
                headers.append("Authorization", "Bearer ${testServer.authToken}")
            }
        }, toolServer.url.resolve()))
    }

    test("testBearerAuthBadToken") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServerBearerAuth(get())
        val httpClient = get<HttpClient>()

        shouldThrow<McpException> {
            testTransport(testServer, StreamableHttpClientTransport(httpClient.config {
                defaultRequest {
                    headers.append("Authorization", "Bearer badToken")
                }
            }, toolServer.url.resolve()))
        }
    }

    test("testBearerAuthNoToken") {
        val testServer = TestMcpServer()
        val toolServer = testServer.asPrototypeToolServerBearerAuth(get())

        shouldThrow<McpException> {
            testTransport(testServer, StreamableHttpClientTransport(get(), toolServer.url.resolve()))
        }
    }
})