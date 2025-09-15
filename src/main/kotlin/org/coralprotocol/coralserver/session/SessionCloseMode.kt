package org.coralprotocol.coralserver.session

enum class SessionCloseMode {
    /**
     * Wait for clean close of agents / connections / etc
     */
    CLEAN,

    /**
     * Wait for nothing, session must die ASAP
     */
    FORCE
}