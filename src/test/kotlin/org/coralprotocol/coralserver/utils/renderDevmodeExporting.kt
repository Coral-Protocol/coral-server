package org.coralprotocol.coralserver.utils

import org.coralprotocol.coralserver.server.CoralServer

/**
 * (Does )
 */

fun ServerConnectionCoreDetails.renderDevmodeExporting(
    server: CoralServer,
    externalId: String,
    agentId: String
): String {

    return "$protocol://$host:$port/sse/v1/export/$externalId?agentId=$agentId&agentDescription=$descriptionPassedToServer"
}