package org.coralprotocol.coralserver.config

import java.nio.file.Path

data class OpenShellConfig(
    val supervisorPath: Path? = null,
    val regoTemplatePath: Path? = null,
    val supervisorMountPath: String = "/usr/local/bin/openshell-sandbox",
    val policyMountPath: String = "/policy",
) {
    val available: Boolean
        get() = supervisorPath?.toFile()?.canExecute() == true
}
