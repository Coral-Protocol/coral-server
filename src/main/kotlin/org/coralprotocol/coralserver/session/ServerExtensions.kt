package org.coralprotocol.coralserver.session

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Extension property to get the session associated with a server.
 */
val Server.session: Session?
    get() = SessionManager.getAllSessions().find { it.servers.contains(this) }