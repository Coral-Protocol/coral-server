package org.coralprotocol.coralserver.config

import java.nio.file.Path

data class ConsoleConfig(
    /**
     * The path that console related files will be downloaded into and served from
     */
    val cachePath: String = Path.of(System.getProperty("user.home"), ".coral", "console").toString(),

    /**
     * The URL to download console release from.  The version will be appended to the end of this with a / between them
     */
    val consoleReleaseUrl: String = "https://github.com/Coral-Protocol/coral-studio/releases/download",

    /**
     * The version of the console to download, will be appended to [consoleReleaseUrl]
     */
    val consoleReleaseVersion: String = "0.2.1",

    /**
     * The name of the zip bundle in the release
     */
    val bundleName: String = "bundle.zip",

    /**
     * If this is true, other folders in [cachePath] that are not relevant to the [consoleReleaseVersion] will be
     * deleted.
     */
    val deleteOldVersions: Boolean = true,

    /**
     * If this is true, the specified console bundle will be downloaded on every launch, replacing the last bundle with
     * the same versions
     */
    val alwaysDownload: Boolean = false
)