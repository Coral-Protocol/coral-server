package org.coralprotocol.coralserver.e2e

import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetails

/**
 * Used in local cases
 */
fun ServerConnectionCoreDetails.renderWithSessionDetails(session: LocalSession) =
    "$protocol://$host:$port/sse/v1/devmode/${session.applicationId}/${session.privacyKey}/${session.id}/sse?agentId=${namePassedToServer}&agentDescription=$descriptionPassedToServer"