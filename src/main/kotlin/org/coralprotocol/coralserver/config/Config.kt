@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.config

import io.ktor.http.*
import kotlinx.serialization.*
import mu.KotlinLogging
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.indexer.GitRegistryAgentIndexer
import org.coralprotocol.coralserver.agent.registry.indexer.NamedRegistryAgentIndexer
import org.coralprotocol.coralserver.agent.registry.indexer.RegistryAgentIndexer
import org.coralprotocol.coralserver.util.isWindows
import java.io.File
import java.nio.file.Path

private val logger = KotlinLogging.logger {  }

const val CORAL_MAINNET_MINT =  "CoRAitPvr9seu5F9Hk39vbjqA1o1XuoryHjSk1Z1q2mo"
const val CORAL_DEV_NET_MINT = "FBrR4v7NSoEdEE9sdRN1aE5yDeop2cseaBbfPVbJmPhf"

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

fun defaultDockerAddress(): String {
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

@Serializable
data class PaymentConfig(
    /**
     * The path to the configured wallet
     */
    val walletPath: String = Path.of(System.getProperty("user.home"), ".coral", "wallet.toml").toString(),

    /**
     * The RPC url for payments
     */
    val rpcUrl: String = "https://api.devnet.solana.com",
) {

    /**
     * The configured wallet for this server.  Required to send and receive payments.
     */
    @Transient
    val wallet: Wallet? = run {
        val file = File(walletPath)
        if (file.exists()) {
            try {
                return@run toml.decodeFromString<Wallet>(file.readText())
            }
            catch (e: Exception) {
                logger.warn(e) { "Failed to load wallet file $walletPath" }
            }
        }
        else {
            logger.warn { "No wallet file found at $walletPath" }
        }

        null
    }
}

@Serializable
data class NetworkConfig(
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
)

@Serializable
data class RegistryConfig(
    @SerialName("indexers")
    private val configIndexers: LinkedHashMap<String, RegistryAgentIndexer> = linkedMapOf()
) {
    /**
     * Default Coral indexer.  For now this will be the temporary Git indexer.
     */
    @Transient
    private val coralIndexer = NamedRegistryAgentIndexer("coral", GitRegistryAgentIndexer("https://github.com/Coral-Protocol/marketplace", 0))

    /**
     * A list of indexers including the built-in Coral indexer.
     */
    val indexers: Map<String, RegistryAgentIndexer>
        get() {
            val map = configIndexers.toMutableMap()
            map["coral"] = coralIndexer.indexer

            return map
        }

    /**
     * If the given name is null, returns the indexer with the highest priority.  If a name is specified, return an
     * indexer with a matching name or throw a RegistryException.  If the given name is null and there are no defined
     * indexers, this returns the default Coral indexer.
     *
     * The name "coral" is reserved for the Coral indexer and can be used to explicitly request it.
     */
    fun getIndexer(name: String?): NamedRegistryAgentIndexer {
        return when (name) {
            "coral" -> {
                coralIndexer
            }
            null -> {
                configIndexers.map { (name, indexer) ->
                    NamedRegistryAgentIndexer(name, indexer)
                }.maxByOrNull { it.indexer.priority } ?: coralIndexer
            }
            else -> {
                NamedRegistryAgentIndexer(name, configIndexers[name] ?: throw RegistryException("No indexer found with name $name"))
            }
        }
    }
}

@Serializable
data class CacheConfig(
    @SerialName("root_path")
    private val rootPathString: String = Path.of(System.getProperty("user.home"), ".coral").toString(),

    @SerialName("indexer_cache_path")
    private val indexerCachePath: String = Path.of(rootPathString, "registry").toString(),

    @SerialName("agent_cache_path")
    private val agentCacheString: String = Path.of(rootPathString, "agent").toString(),
) {
    @Transient
    val root = Path.of(rootPathString)

    @Transient
    val index = Path.of(indexerCachePath)

    @Transient
    val agent = Path.of(agentCacheString)
}

@Serializable
data class SecurityConfig(
    /**
     * If this is false, coral-agent.toml files imported from Git, agent indexers or local paths will not be allowed to
     * contain an export section.  It is recommended to keep this value set to false unless you have a good reason to
     * set it to true and understand the risks involved.
     */
    val enableReferencedExporting: Boolean = false,
)

@Serializable
data class Config(
    @SerialName("payments")
    val paymentConfig: PaymentConfig = PaymentConfig(),

    @SerialName("network")
    val networkConfig: NetworkConfig = NetworkConfig(),

    @SerialName("docker")
    val dockerConfig: DockerConfig = DockerConfig(),

    @SerialName("registry")
    val registryConfig: RegistryConfig = RegistryConfig(),

    @SerialName("cache")
    val cache: CacheConfig = CacheConfig(),

    @SerialName("security")
    val security: SecurityConfig = SecurityConfig(),
) {
    /**
     * Calculates the address required to access the server for a given consumer.
     */
    fun resolveAddress(consumer: AddressConsumer): String {
        return when (consumer) {
            AddressConsumer.EXTERNAL -> networkConfig.externalAddress
            AddressConsumer.CONTAINER -> dockerConfig.address
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
            port = networkConfig.bindPort.toInt()
        ).build()

    /**
     * Runs the update method on all configured indexers
     */
    fun updateIndexes() {
        registryConfig.indexers.forEach { (name, indexer) ->
            indexer.update(this, name)
        }
    }
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