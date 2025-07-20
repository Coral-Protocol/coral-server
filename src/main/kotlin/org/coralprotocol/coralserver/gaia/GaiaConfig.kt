package org.coralprotocol.coralserver.gaia

import java.io.File

enum class GaiaQuestionSet(
    val setId: String,
    val metadataFile: File,
    val resultsDir: File = File("coral-GAIA/results-divided/$setId")
) {
    VALIDATION("validation", File("coral-GAIA/data/gaia/2023/validation/metadata.jsonl")),
    TEST("test", File("coral-GAIA/data/gaia/2023/test/metadata.jsonl")),
}

object GaiaConfig {
    val maxPassesPerTask: Int = 3
    val gaiaQuestionSet = GaiaQuestionSet.VALIDATION
    val multiAgentSystemRootDir =
        File("coral-GAIA")
            .apply { if (!exists()) throw IllegalStateException("Multi-agent system root directory does not exist: $absolutePath" +
                    "Make sure to clone the accompanying coral-gaia repo in ${absoluteFile.parent}") }

    fun getOrCreateSessionWorkingDirectory(questionId: String): File {
        return File("tmp/$questionId/gaia/executable").apply {
            mkdirs()
        }
    }
}