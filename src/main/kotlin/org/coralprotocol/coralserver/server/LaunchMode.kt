package org.coralprotocol.coralserver.server

enum class LaunchMode {
    /**
     * Development launch mode will disable various security features that will aid in creating agents.  Do not use this
     * mode on a production server.
     */
    DEVELOPMENT,

    /**
     * Run mode for multi-tenancy.
     */
    SHARED,

    /**
     * Production
     */
    DEDICATED,
}