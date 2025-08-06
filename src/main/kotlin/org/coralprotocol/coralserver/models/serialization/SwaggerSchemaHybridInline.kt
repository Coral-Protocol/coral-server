package org.coralprotocol.coralserver.models.serialization

import io.github.smiley4.schemakenerator.core.data.TypeData
import io.github.smiley4.schemakenerator.core.data.TypeId
import io.github.smiley4.schemakenerator.swagger.SwaggerSchemaCompileUtils.copyTypeToTypes
import io.github.smiley4.schemakenerator.swagger.SwaggerSchemaCompileUtils.merge
import io.github.smiley4.schemakenerator.swagger.SwaggerSchemaCompileUtils.resolveReferences
import io.github.smiley4.schemakenerator.swagger.SwaggerSchemaCompileUtils.shouldReference
import io.github.smiley4.schemakenerator.swagger.SwaggerSchemaUtils
import io.github.smiley4.schemakenerator.swagger.data.CompiledSwaggerSchemaData
import io.github.smiley4.schemakenerator.swagger.data.IntermediateSwaggerSchemaData
import io.github.smiley4.schemakenerator.swagger.data.SwaggerSchemaData
import io.swagger.v3.oas.models.media.Schema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator
import nl.adaptivity.xmlutil.core.impl.multiplatform.name

@OptIn(ExperimentalSerializationApi::class)
internal class SwaggerSchemaHybridInlineStep (
    private val explicitNullTypes: Boolean,
    private val pathBuilder: (type: TypeData, types: Map<TypeId, TypeData>) -> String
) {

    private val schemaUtils = SwaggerSchemaUtils()

    private class Context(
        /**
         * all known input swagger schemas
         */
        val knownSchemas: List<SwaggerSchemaData>,
        /**
         * all known input types
         */
        val knownTypeData: Map<TypeId, TypeData>,
        /**
         *  the current list of schemas in the components section. Add new ones to this list.
         */
        val components: MutableMap<String, Schema<*>>,
        /**
         * counts how often any (original) path is used in the component-section
         */
        val pathCounters: MutableMap<String, Int>,
        /**
         * already mapped reference paths for types. Add new paths to this map.
         */
        val refPathMapping: MutableMap<TypeId, String>,
    ) {
        companion object {
            fun from(data: IntermediateSwaggerSchemaData) = Context(
                data.entries,
                data.typeDataById,
                mutableMapOf(),
                mutableMapOf(),
                mutableMapOf(),
            )
        }
    }


    /**
     * Put referenced schemas into definitions and reference them
     */
    fun compile(input: IntermediateSwaggerSchemaData): CompiledSwaggerSchemaData {
        val context = Context.from(input)

        copyTypeToTypes(context.knownSchemas)

        val root = resolveReferences(input.rootSchema) { refObj ->
            resolveReference(refObj, context)
        }

        handleDiscriminatorMappings(root, context.components, context.knownTypeData)

        return CompiledSwaggerSchemaData(
            typeData = input.rootTypeData,
            swagger = root,
            componentSchemas = context.components
        )
    }

    fun compileRoot(input: IntermediateSwaggerSchemaData): CompiledSwaggerSchemaData {
        val result = SwaggerSchemaHybridInlineStep(explicitNullTypes, pathBuilder).compile(input)
        if (shouldReference(result.swagger, result.typeData)) {
            val refPath = pathBuilder(result.typeData, input.typeDataById)
            return CompiledSwaggerSchemaData(
                typeData = result.typeData,
                swagger = schemaUtils.referenceSchema(refPath, true),
                componentSchemas = buildMap {
                    this.putAll(result.componentSchemas)
                    this[refPath] = result.swagger
                }
            )
        } else {
            return CompiledSwaggerSchemaData(
                typeData = result.typeData,
                swagger = result.swagger,
                componentSchemas = result.componentSchemas
            )
        }
    }


    /**
     * Handles a schema object referencing another schema using a temporary reference path.
     * @param refObj the object with the temporary reference path
     * @param context the current compile context with data about input schemas and type data as well as current produced information
     */
    private fun resolveReference(refObj: Schema<*>, context: Context): Schema<*> {
        // find actual schema data
        val referencedSchema = context.knownSchemas.find { it.typeData.id == TypeId(refObj.`$ref`) }
        if(referencedSchema == null) {
            return refObj
        }

        // create swagger property with correct reference path (and add actual schema to context)
        val property = if (shouldReference(referencedSchema.swagger, referencedSchema.typeData)
            && referencedSchema.typeData.annotations.firstOrNull { it.name == JsonClassDiscriminator::class.name } == null) {
            createRefProperty(refObj, referencedSchema, context)
        } else {
            createInlineProperty(refObj, referencedSchema)
        }

        // add back some information to property
        if(!refObj.description.isNullOrEmpty()) {
            property.description = refObj.description
        }

        // return
        return property
    }


    /**
     * Create a swagger-schema with a proper reference to replace the pending referencing schema.
     * @param refObj the object with the temporary reference path
     * @param schema the referenced schema
     * @param context the current compile context with data about input schemas and type data as well as current produced information
     */
    private fun createRefProperty(
        refObj: Schema<*>,
        schema: SwaggerSchemaData,
        context: Context,
    ): Schema<*> {
        val refPath = if (context.refPathMapping.containsKey(schema.typeData.id)) {
            context.refPathMapping[schema.typeData.id]!!
        } else {
            var newRefPath = pathBuilder(schema.typeData, context.knownTypeData)
            context.pathCounters[newRefPath] = (context.pathCounters[newRefPath] ?: 0) + 1
            if (context.components.containsKey(newRefPath)) {
                newRefPath += context.pathCounters[newRefPath]
            }
            context.refPathMapping[schema.typeData.id] = newRefPath
            context.components[newRefPath] = placeholder() // break out of infinite loops
            context.components[newRefPath] = resolveReferences(schema.swagger) { resolveReference(it, context) }
            newRefPath
        }

        return if (refObj.nullable == true && explicitNullTypes) {
            schemaUtils.referenceSchemaNullable(refPath, true)
        } else {
            schemaUtils.referenceSchema(refPath, true)
        }
    }


    /**
     * Create an inline swagger-schema to replace the pending referencing schema.
     * @param refObj the schema containing the reference
     * @param schema the schema referenced by [refObj]
     */
    private fun createInlineProperty(refObj: Schema<*>, schema: SwaggerSchemaData): Schema<*> {
        return merge(refObj, schema.swagger).also {
            if (it.nullable == true && explicitNullTypes) {
                it.types = setOf("null") + it.types
            }
            it.nullable = null
        }
    }


    /**
     * Replace temporary reference paths in discriminator mappings
     * @param root the root schema
     * @param components swagger schema components section. Adds new referenced schemas.
     * @param knownTypeData all input type data
     */
    private fun handleDiscriminatorMappings(
        root: Schema<*>,
        components: MutableMap<String, Schema<*>>,
        knownTypeData: Map<TypeId, TypeData>
    ) {
        handleDiscriminatorMappings(root, knownTypeData)
        components.forEach { (_, schema) -> handleDiscriminatorMappings(schema, knownTypeData) }
    }


    /**
     * Replace temporary reference paths in discriminator mappings
     * @param schema the current schema to process
     * @param knownTypeData all input type data
     */
    private fun handleDiscriminatorMappings(schema: Schema<*>, knownTypeData: Map<TypeId, TypeData>) {
        if (schema.discriminator?.mapping == null) {
            return
        }
        schema.discriminator.mapping = schema.discriminator.mapping.mapValues { (_, target) ->
            val referencedType = knownTypeData[TypeId(target)]!!
            schemaUtils.componentReference(pathBuilder(referencedType, knownTypeData))
        }
    }


    /**
     * @return a placeholder schema object
     */
    private fun placeholder() = Schema<Any>()

}

/**
 * Resolves references in generated swagger schemas by collecting them in the components-section and referencing them.
 * @param explicitNullTypes whether to explicitly add "null" as a type to nullable fields.
 * @param builder builds the path to reference the type, i.e. which "name" to use
 */
fun IntermediateSwaggerSchemaData.compileHybridInline(
    explicitNullTypes: Boolean = true,
    builder: (type: TypeData, types: Map<TypeId, TypeData>) -> String
): CompiledSwaggerSchemaData {
    return SwaggerSchemaHybridInlineStep(explicitNullTypes, builder).compileRoot(this)
}
