package org.coralprotocol.coralserver.session

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustPolicyResolver
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionTransport
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.DockerRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.agent.execution.toHostConfig
import org.coralprotocol.coralserver.agent.runtime.sanitizeDockerImageName
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.RootConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingEvent
import org.coralprotocol.coralserver.modules.LOGGER_LOCAL_SESSION
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.shouldPostEvents
import org.koin.core.qualifier.named
import org.koin.test.inject
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds
import com.github.dockerjava.api.model.Capability

/**
 * Because these tests interact with a system docker installation, it is generally recommended to skip them.  For
 * example, pulling a Docker image is the first test here, and it will attempt to pull alpine:3.23.0. Because previous
 * tests will have installed this, it will be removed before being pulled - which can be annoying on a system that
 * may have been using that image.  In addition, this will not kill containers that might be using that image, so that
 * test will fail if the image is being used by a running container.
 *
 * These tests are valuable but require a semi-pristine testing environment.
 */
class DockerRuntimeTest : CoralTest({
    val image = "alpine:3.23.0"

    fun isDockerAvailable(testCase: TestCase): Boolean {
        try {
            // sessionTest will not configure Docker past the defaults
            val config = RootConfig()

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
    test("testDockerPull").config(
        invocations = 1,
        invocationTimeout = 60.seconds,
        enabledIf = ::isDockerAvailable
    ) {
        val applicationRuntimeContext by inject<ApplicationRuntimeContext>()

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

    test("testDockerRuntime").config(
        invocations = 1,
        invocationTimeout = 180.seconds,
        enabledIf = ::isDockerAvailable
    ) {
        val localSessionManager by inject<LocalSessionManager>()
        val logger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val agent1Name = "agent1"
        val optionValue1 = UUID.randomUUID().toString()
        val optionValue2 = UUID.randomUUID().toString()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        provider = GraphAgentProvider.Local(RuntimeId.DOCKER)
                        registryAgent {
                            runtime(
                                DockerRuntime(
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
                            )
                        }
                        option(
                            "TEST_OPTION", AgentOptionWithValue.String(
                                option = AgentOption.String(),
                                value = AgentOptionValue.String(optionValue1)
                            )
                        )
                        option(
                            "TEST_FS_OPTION", AgentOptionWithValue.String(
                                option = run {
                                    val opt = AgentOption.String()
                                    opt.transport = AgentOptionTransport.FILE_SYSTEM
                                    opt
                                },
                                value = AgentOptionValue.String(optionValue2)
                            )
                        )
                    }
                )
            )
        )

        shouldPostEvents(
            timeout = 3.seconds,
            allowUnexpectedEvents = true,
            events = mutableListOf(
                TestEvent("value 1") { it is LoggingEvent.Info && it.text == optionValue1 },
                TestEvent("value 2") { it is LoggingEvent.Info && it.text == optionValue2 },
                TestEvent("secret") { it is LoggingEvent.Info && it.text == unitTestSecret }
            ),
            logger.flow
        ) {
            session1.fullLifeCycle()
        }
    }

    test("testDockerRuntimeCleanup").config(
        invocations = 1,
        invocationTimeout = 30.seconds,
        enabledIf = ::isDockerAvailable
    ) {
        val localSessionManager by inject<LocalSessionManager>()
        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1") {
                        provider = GraphAgentProvider.Local(RuntimeId.DOCKER)
                        registryAgent {
                            runtime(
                                DockerRuntime(
                                    image = image,
                                    command = listOf("sh", "-c", """sleep 1000""".trimIndent())
                                )
                            )
                        }
                    }
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

        session1.sessionScope.cancel()
    }

    test("testDockerHostConfigHardeningDefaults") {
        val logger by inject<Logger>(named(LOGGER_LOCAL_SESSION))
        val resolver by inject<ExecutionTrustPolicyResolver>()
        val dockerConfig by inject<DockerConfig>()

        val hostConfig = resolver.resolve(AgentRegistrySourceIdentifier.Local).docker.toHostConfig(emptyList(), logger)
        val tier = dockerConfig.trusted

        hostConfig.privileged shouldBe false
        hostConfig.readonlyRootfs shouldBe tier.readOnlyRootFilesystem
        hostConfig.securityOpts?.shouldContain("no-new-privileges")
        hostConfig.capDrop?.toSet() shouldBe setOf(Capability.ALL)
        hostConfig.pidsLimit shouldBe tier.pidsLimit
        hostConfig.nanoCPUs shouldBe tier.nanoCpus
        hostConfig.memory shouldBe tier.memoryLimitBytes
    }

    test("testDockerImageDigestRequiredForMarketplaceAgents") {
        val logger by inject<Logger>(named(LOGGER_LOCAL_SESSION))
        val identifier = RegistryAgentIdentifier(
            name = "market-agent",
            version = "1.0.0",
            registrySourceId = AgentRegistrySourceIdentifier.Marketplace
        )

        shouldThrow<IllegalArgumentException> {
            sanitizeDockerImageName(
                imageName = "ghcr.io/coral-protocol/agent:1.0.0",
                id = identifier,
                logger = logger,
                requireDigest = true
            )
        }

        sanitizeDockerImageName(
            imageName = "ghcr.io/coral-protocol/agent@sha256:abc123",
            id = identifier,
            logger = logger,
            requireDigest = true
        ) shouldBe "ghcr.io/coral-protocol/agent@sha256:abc123"
    }

    test("testMarketplaceDockerRuntimeHardening").config(
        invocations = 1,
        invocationTimeout = 180.seconds,
        enabledIf = ::isDockerAvailable
    ) {
        val localSessionManager by inject<LocalSessionManager>()
        val logger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val optionValue = UUID.randomUUID().toString()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("marketplace") {
                        provider = GraphAgentProvider.Local(RuntimeId.DOCKER)
                        registryAgent {
                            registrySourceId = AgentRegistrySourceIdentifier.Marketplace
                            runtime(
                                DockerRuntime(
                                    image = image,
                                    command = listOf(
                                        "sh", "-c", """
                                            echo HOME:
                                            echo ${'$'}HOME

                                            echo UID:
                                            id -u

                                            touch /coral-rootfs-test 2>/dev/null || echo ROOT_FS_READ_ONLY

                                            echo TEST_FS_OPTION:
                                            cat ${'$'}TEST_FS_OPTION
                                        """.trimIndent()
                                    )
                                )
                            )
                        }
                        option(
                            "TEST_FS_OPTION", AgentOptionWithValue.String(
                                option = run {
                                    val opt = AgentOption.String()
                                    opt.transport = AgentOptionTransport.FILE_SYSTEM
                                    opt
                                },
                                value = AgentOptionValue.String(optionValue)
                            )
                        )
                    }
                )
            )
        )

        shouldPostEvents(
            timeout = 10.seconds,
            allowUnexpectedEvents = true,
            events = mutableListOf(
                TestEvent("home tmp") { it is LoggingEvent.Info && it.text == "/tmp" },
                TestEvent("uid") { it is LoggingEvent.Info && it.text == "65532" },
                TestEvent("rootfs readonly") { it is LoggingEvent.Info && it.text == "ROOT_FS_READ_ONLY" },
                TestEvent("fs readable") { it is LoggingEvent.Info && it.text == optionValue },
            ),
            logger.flow
        ) {
            session1.fullLifeCycle()
        }
    }
})
