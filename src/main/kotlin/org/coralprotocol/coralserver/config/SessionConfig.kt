package org.coralprotocol.coralserver.config

data class SessionConfig(
    /**
     * The default number of milliseconds that wait tooling should take before timing out.  Note that some clients
     * e.g (the Kotlin MCP client) force network timeouts and often cause undesirable errors if those timeouts occur
     * instead of this timeout that will tool responses back
     */
    val defaultWaitTimeout: Long = 60000
)