package org.coralprotocol.coralserver.util

import java.util.*

object CoralServerVersion {
    private const val RESOURCE = "/org/coralprotocol/coralserver/server-version.properties"

    /**
     * The version of this Coral Server build, taken from gradle.properties when Gradle processes resources.
     * Falls back to "unknown" when the resource was copied without Gradle's processing, e.g. by a non-delegated IDE build.
     */
    val current: String by lazy {
        CoralServerVersion::class.java.getResourceAsStream(RESOURCE)
            ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
            ?.takeUnless { it.isBlank() || it.startsWith("\${") }
            ?: "unknown"
    }
}
