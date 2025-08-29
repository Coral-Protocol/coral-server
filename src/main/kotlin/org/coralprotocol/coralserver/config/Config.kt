package org.coralprotocol.coralserver.config

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.util.isWindows
import java.io.File

private fun defaultDockerSocket(): String {
    val specifiedSocket = System.getProperty("CORAL_DOCKER_SOCKET")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("docker.host")?.takeIf { it.isNotBlank() }
        ?: System.getenv("DOCKER_SOCKET")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("docker.socket")?.takeIf { it.isNotBlank() }

    if (specifiedSocket != null) {
        return specifiedSocket
    }

    if (isWindows()) {
        // Required if using Docker for Windows.  Note that this also requires a transport client that supports named
        // pipes, e.g., httpclient5
        return "npipe:////./pipe/docker_engine"
    }
    else {
        // Check whether colima is installed and use its socket if available
        val homeDir = System.getProperty("user.home")
        val colimaSocket = "$homeDir/.colima/default/docker.sock"

        return if (File(colimaSocket).exists()) {
            "unix://$colimaSocket"
        } else {
            // Default Docker socket
            "unix:///var/run/docker.sock"
        }
    }
}

@Serializable
data class Network(
    /**
     * The network address to bind the HTTP server to
     */
    @SerialName("bind_address")
    val bindAddress: String = "0.0.0.0",

    /**
     * The external address that can be used to access this server.  E.g., domain name.
     * This should not include a port
     */
    @SerialName("external_address")
    val externalAddress: String = bindAddress,

    /**
     * The port to bind the HTTP server to
     */
    @SerialName("bind_port")
    val bindPort: UShort = 5555u,
)

@Serializable
data class Docker(
    /**
     * Optional docker socket path
     */
    val socket: String = defaultDockerSocket(),

    /**
     * An address that can be used to access the host machine from inside a Docker container.  Note if nested Docker is
     * used, the default here might not be correct.
     */
    val address: String = "host.docker.internal",

    /**
     * The number of seconds to wait for a response from a Docker container before timing out.
     */
    val responseTimeout: Long = 30,

    /**
     * The number of seconds to wait for a connection to a Docker container before timing out.
     * Note that on Docker for Windows, if the Docker engine is not running, this timeout will be met.
     */
    val connectionTimeout: Long = 30,

    /**
     * Max number of connections to running Docker containers.
     */
    val maxConnections: Int = 1024,


    )


@Serializable
data class Config(
    val network: Network = Network(),
    val docker: Docker = Docker()
) {
    /**
     * Calculates the address required to access the server for a given consumer.
     */
    fun resolveAddress(consumer: AddressConsumer): String {
        return when (consumer) {
            AddressConsumer.EXTERNAL -> network.externalAddress
            AddressConsumer.CONTAINER -> docker.address
            AddressConsumer.LOCAL -> "localhost"
        }
    }

    /**
     * Calculates the base URL required to access the server for a given consumer.
     */
    fun resolveBaseUrl(consumer: AddressConsumer): Url =
        URLBuilder(
            protocol = URLProtocol.HTTP,
            host = resolveAddress(consumer),
            port = network.bindPort.toInt()
        ).build()
}

enum class AddressConsumer {
    /**
     * Another computer/server
     */
    EXTERNAL,

    /**
     * A container ran on the same machine as the server
     */
    CONTAINER,

    /**
     * A process running on the same machine as the server
     */
    LOCAL
}