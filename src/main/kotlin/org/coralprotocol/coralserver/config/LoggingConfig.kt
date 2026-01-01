package org.coralprotocol.coralserver.config

data class LoggingConfig(
    /**
     * The number of logging events to store in memory
     */
    val logBufferSize: UInt = 32u * 1024u,

    /**
     * Maximum number of logging events to replay to a new subscriber, regardless of what they requested
     */
    val maxReplay: UInt = 2048u
)
