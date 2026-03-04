package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description

interface SessionResource {
    @Description("A map of annotations for this session-related resource")
    val annotations: Map<String, String>
}