package org.coralprotocol.coralserver.session

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.DockerRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.LogMessage
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Because these tests interact with a system docker installation, it is generally recommended to skip them.  For
 * example, pulling a Docker image is the first test here, and it will attempt to pull alpine:3.23.0. Because previous
 * tests will have installed this, it will be removed before being pulled - which can be annoying on a system that
 * may have been using that image.  In addition, this will not kill containers that might be using that image, so that
 * test will fail if the image is being used by a running container.
 *
 * These tests are valuable but require a semi-pristine testing environment.
 */
class DockerRuntimeTest : FunSpec({
    val image = "alpine:3.23.0"

    fun isDockerAvailable(testCase: TestCase): Boolean {
        try {
            // sessionTest will not configure Docker past the defaults
            val config = Config()

            val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.dockerConfig.socket)
                .build()

            val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.dockerHost)
                .sslConfig(dockerClientConfig.sslConfig)
                .responseTimeout(Duration.ofSeconds(1))
                .connectionTimeout(Duration.ofSeconds(1))
                .build()

            DockerClientImpl.getInstance(dockerClientConfig, httpClient)
                .pingCmd().exec()

            return true;
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * The timeouts for other tests do not account for pull time, so this test must be run first.
     */
    test("testDockerPull").config(timeout = 60.seconds, enabledIf = ::isDockerAvailable) {
        sessionTest {
            shouldNotThrowAny {
                withContext(Dispatchers.IO) {
                    val client = applicationRuntimeContext.dockerClient.shouldNotBeNull()

                    // Remove the image if it exists, for a clean pull
                    client.shouldNotBeNull()
                        .listImagesCmd()
                        .exec()
                        .forEach {
                            if (it.repoTags?.contains(image) == true) {
                                client
                                    .removeImageCmd(it.id)
                                    .withForce(true)
                                    .exec()
                            }
                        }

                    // Pull again
                    client.pullImageCmd(image)
                        .exec(object : ResultCallback.Adapter<PullResponseItem>() {

                        })
                        .awaitCompletion()
                }
            }
        }
    }

    test("testDockerRuntime").config(timeout = 180.seconds, enabledIf = ::isDockerAvailable) {
        val agent1Name = "agent1"
        val optionValue1 = UUID.randomUUID().toString()
        val optionValue2 = UUID.randomUUID().toString()

        sessionTest {
            val (session1, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = agent1Name,
                                dockerRuntime = DockerRuntime(
                                    image = image,
                                    command = listOf(
                                        "sh", "-c", """
                                            echo TEST_OPTION:
                                            echo ${'$'}TEST_OPTION
                                            
                                            echo UNIT_TEST_SECRET:
                                            echo ${'$'}UNIT_TEST_SECRET
                                            
                                            echo TEST_FS_OPTION:
                                            cat ${'$'}TEST_FS_OPTION
                                        """.trimIndent()
                                    )
                                )
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.DOCKER),
                            options = mapOf(
                                "TEST_OPTION" to AgentOptionWithValue.String(
                                    option = AgentOption.String(),
                                    value = AgentOptionValue.String(optionValue1)
                                ),
                                "TEST_FS_OPTION" to AgentOptionWithValue.String(
                                    option = run {
                                        val opt = AgentOption.String()
                                        opt.transport = AgentOptionTransport.FILE_SYSTEM
                                        opt
                                    },
                                    value = AgentOptionValue.String(optionValue2)
                                )
                            )
                        ),
                    ),
                    customTools = mapOf(),
                    groups = setOf()
                )
            )

            // collect messages written to stdout by agent1
            val collecting = CompletableDeferred<Unit>()
            val messages = mutableListOf<String>()
            val agent1 = session1.getAgent(agent1Name)
            val collector = session1.sessionScope.launch {
                collecting.complete(Unit)
                agent1.logger.getSharedFlow().collect {
                    if (it is LogMessage.Info)
                        messages.add(it.message)
                }
            }

            // no exceptions should be thrown for agent1, run agent1 until it exits
            collecting.await()
            session1.fullLifeCycle()

            // Test that the script printed both env and fs option values
            messages.shouldContain(optionValue1)
            messages.shouldContain(optionValue2)

            messages.shouldContain(unitTestSecret)

            collector.cancelAndJoin()
        }
    }

    test("testDockerRuntimeCleanup").config(timeout = 30.seconds, enabledIf = ::isDockerAvailable) {
        sessionTest {
            val (session1, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = "agent1",
                                dockerRuntime = DockerRuntime(
                                    image = image,
                                    command = listOf("sh", "-c", """sleep 1000""".trimIndent())
                                )
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.DOCKER),
                            options = mapOf()
                        ),
                    ),
                    customTools = mapOf(),
                    groups = setOf()
                )
            )

            session1.shouldPostEvents(
                timeout = 15.seconds,
                events = mutableListOf(
                    TestEvent("agent1 runtime started") { it is SessionEvent.RuntimeStarted },
                    TestEvent("agent1 container created") { it is SessionEvent.DockerContainerCreated },
                ),
            ) {
                session1.launchAgents()
            }

            session1.shouldPostEvents(
                timeout = 15.seconds,
                events = mutableListOf(
                    TestEvent("agent1 container removed") { it is SessionEvent.DockerContainerRemoved },
                ),
            ) {
                session1.cancelAndJoinAgents()
            }
        }
    }
})