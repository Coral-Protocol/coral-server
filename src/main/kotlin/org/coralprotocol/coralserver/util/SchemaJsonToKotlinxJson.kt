package org.coralprotocol.coralserver.util

import io.github.smiley4.schemakenerator.jsonschema.data.CompiledJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonBooleanValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNullValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNumericValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive


fun CompiledJsonSchemaData.convert(): JsonElement {
    if (definitions.isNotEmpty()) {
        throw IllegalArgumentException(
            "Can not convert json schema to kotlinx.serialization objects. Add the `merge()`-step before this step."
        )
    }
    return json.convert()
}

fun JsonNode.convert(): JsonElement {
    return when (this) {
        is JsonArray -> this.convert()
        is JsonObject -> this.convert()
        is JsonBooleanValue -> this.convert()
        is JsonNumericValue -> this.convert()
        is JsonTextValue -> this.convert()
        is JsonNullValue -> kotlinx.serialization.json.JsonNull
    }
}

fun JsonArray.convert(): JsonElement {
    return kotlinx.serialization.json.JsonArray(
        this.items.map { it.convert() }
    )
}

fun JsonObject.convert(): JsonElement {
    return kotlinx.serialization.json.JsonObject(
        this.properties.mapValues { (_, value) -> value.convert() }
    )
}

fun JsonBooleanValue.convert() = JsonPrimitive(this.value)

fun JsonNumericValue.convert() = JsonPrimitive(this.value)

fun JsonTextValue.convert() = JsonPrimitive(this.value)