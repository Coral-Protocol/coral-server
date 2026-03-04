package org.coralprotocol.coralserver.config

import java.util.*

data class NetworkConfig(
    /**
     * The network address to bind the HTTP server to
     */
    val bindAddress: String = "0.0.0.0",

    /**
     * The external address that can be used to access this server.  E.g., domain name.
     * This should not include a port
     */
    val externalAddress: String = bindAddress,

    /**
     * The port to bind the HTTP server to
     */
    val bindPort: UShort = 5555u,

    /**
     * Allows anyHost in the server's CORS settings.  Should only be used for development
     */
    val allowAnyHost: Boolean = false,

    /**
     * The secret used to encrypt webhook callouts
     */
    val webhookSecret: String = UUID.randomUUID().toString(),


    /**
     * The secret used to data sent via agent's custom tools
     */
    val customToolSecret: String = UUID.randomUUID().toString()
)