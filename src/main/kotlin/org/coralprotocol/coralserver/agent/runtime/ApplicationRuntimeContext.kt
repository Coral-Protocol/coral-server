package org.coralprotocol.coralserver.agent.runtime

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import java.time.Duration

private val logger = KotlinLogging.logger {}

class ApplicationRuntimeContext(val config: Config = Config()) {
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
        }
        catch (e: Exception) {
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
        href(ResourcesFormat(), Mcp.Sse(executionContext.agent.secret), builder)

        return builder.build()
    }
}