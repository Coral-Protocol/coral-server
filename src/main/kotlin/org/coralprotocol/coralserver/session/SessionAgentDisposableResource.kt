package org.coralprotocol.coralserver.session

import org.apache.commons.io.file.PathUtils.deleteFile
import org.coralprotocol.coralserver.config.DockerConfig
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempFile
import kotlin.io.path.name
import kotlin.io.path.writeBytes

sealed interface SessionAgentDisposableResource {
    fun dispose()

    class TemporaryFile(data: ByteArray, dockerConfig: DockerConfig) : SessionAgentDisposableResource {
        val file = createTempFile(suffix = ".car") // coral agent resource
        val mountPath = "${dockerConfig.containerTemporaryDirectory}${dockerConfig.containerNameSeparator}${file.name}"
        init {
            file.writeBytes(data)
            runCatching {
                Files.setPosixFilePermissions(
                    file,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ,
                    )
                )
            }
        }

        override fun dispose() {
            deleteFile(file)
        }
    }
}
