package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.RootConfig
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration

class ApplicationRuntimeContext(
    private val config: RootConfig,
) : KoinComponent {
    private val logger by inject<LoggingInterface>()

    val dockerClient = run {
        try {
            val dockerClientConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.dockerConfig.socket)
                .build()

            val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.dockerHost)
                .sslConfig(dockerClientConfig.sslConfig)
                .responseTimeout(Duration.ofSeconds(config.dockerConfig.responseTimeout))
                .connectionTimeout(Duration.ofSeconds(config.dockerConfig.connectionTimeout))
                .maxConnections(config.dockerConfig.maxConnections)
                .build()

            DockerClientImpl.getInstance(dockerClientConfig, httpClient)
        } catch (e: Exception) {
            logger.warn { "Failed to create Docker client: ${e.message}" }
            logger.warn { "Docker runtime will not be available" }
            null
        }
    }

    fun getApiUrl(addressConsumer: AddressConsumer): Url {
        return config.resolveBaseUrl(addressConsumer)
    }

    fun getMcpUrl(executionContext: SessionAgentExecutionContext, addressConsumer: AddressConsumer): Url {
        val builder = URLBuilder(getApiUrl(addressConsumer))
        href(ResourcesFormat(), Mcp.Sse(agentSecret = executionContext.agent.secret), builder)

        return builder.build()
    }
}