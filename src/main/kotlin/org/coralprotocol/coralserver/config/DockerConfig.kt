package org.coralprotocol.coralserver.config

import com.sksamuel.hoplite.ConfigAlias
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.util.isWindows
import java.io.File

private fun defaultDockerAddress(): String {
    if (!isWindows()) {
        val homeDir = System.getProperty("user.home")
        val colimaSocket = "$homeDir/.colima/default/docker.sock"

        // https://stackoverflow.com/questions/48546124/what-is-the-linux-equivalent-of-host-docker-internal/67158212#67158212
        if (!File(colimaSocket).exists()) {
            return "172.17.0.1"
        }
    }

    // host.docker.internal works on Docker for Windows and Colima
    return "host.docker.internal"
}

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
    } else {
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

data class DockerConfig(
    /**
     * Optional docker socket path
     */
    val socket: String = defaultDockerSocket(),

    /**
     * An address that can be used to access the host machine from inside a Docker container.  Note if nested Docker is
     * used, the default here might not be correct.
     */
    val address: String = defaultDockerAddress(),

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

    /**
     * The path separator used in containers.  This is used when sending multiple paths from the host machine
     * (potentially Windows) to a container (in almost all cases Unix).  Windows Containers, though rarely used, do
     * exist.  This config entry serves to support them.
     */
    val containerPathSeparator: Char = ':',

    /**
     * The character used separate names in a path in a container
     *
     * @see [java.nio.file.FileSystem.getSeparator]
     * @see [containerPathSeparator]
     */
    val containerNameSeparator: Char = '/',

    /**
     * The path that temporary files are placed in a container
     *
     * @see [containerPathSeparator]
     */
    val containerTemporaryDirectory: String = "/tmp"
)