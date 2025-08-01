import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Interface for metadata that contains part information
 */
interface PartMetadata {
    val numberOfParts: Int
}

val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

/**
 * Writes metadata to the main file and parts to separate numbered files.
 *
 * @param M The metadata type that implements PartMetadata
 * @param T The type of elements in the part lists
 * @param metadata The metadata to write to the main file
 * @param parts List of lists to write to separate part files
 * @param metadataToString Function to convert metadata to String (defaults to JSON serialization)
 * @param partsToString Function to convert each List<T> to String (defaults to JSON serialization)
 */
inline fun <reified M : PartMetadata, reified T> File.writeTextWithParts(
    metadata: M,
    parts: List<List<T>>,
    metadataToString: (M) -> String = { json.encodeToString(it) },
    partsToString: (List<T>) -> String = { json.encodeToString(it) }
) {
    require(parts.size == metadata.numberOfParts) {
        "Number of parts (${parts.size}) must match metadata.numberOfParts (${metadata.numberOfParts})"
    }

    // Write metadata to the main file
    this.writeText(metadataToString(metadata))

    // Write parts to separate files
    val baseName = this.nameWithoutExtension
    val extension = this.extension
    val parentDir = this.parentFile

    parts.forEachIndexed { index, part ->
        val partNumber = index + 1
        val partFileName = if (extension.isNotEmpty()) {
            "$baseName.part$partNumber.$extension"
        } else {
            "$baseName.part$partNumber"
        }

        val partFile = if (parentDir != null) {
            File(parentDir, partFileName)
        } else {
            File(partFileName)
        }

        partFile.writeText(partsToString(part))
    }
}