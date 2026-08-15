package com.github.caspervg.dbpfmcp.server

import com.xemantic.ai.tool.schema.ObjectSchema
import com.xemantic.ai.tool.schema.generator.generateSchema
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * Derives MCP tool schemas from the same `@Serializable` types the codecs use.
 *
 * Every schema used to be hand-written as a `Tool.Input` literal, roughly 700 lines of them. The
 * result was that not one of the thirty tools declared an `enum`, `minimum`, `maximum`, `pattern`,
 * or `default`; bounds lived in prose and were re-checked at runtime, around twenty-five parameters
 * had no description at all, and no tool declared an output schema. Generating from the request and
 * result types means a constraint is stated once, next to the field, and cannot drift from what the
 * code enforces.
 */
object ToolSchemas {

    private val json = Json { prettyPrint = false }

    /**
     * Schemas are inlined rather than referenced.
     *
     * The generator can emit `$defs` with `$ref` pointers for repeated types (TGI appears in nearly
     * every result), but MCP client support for `$ref` resolution is inconsistent, and a client
     * that cannot follow a reference sees an empty schema.
     */
    private const val INLINE_REFS = true

    inline fun <reified T> input(): Tool.Input = objectSchemaOf<T>().toInput()

    inline fun <reified T> output(): Tool.Output = objectSchemaOf<T>().toOutput()

    inline fun <reified T> objectSchemaOf(): ObjectSchema = schemaFor(serializer<T>().descriptor)

    fun schemaFor(descriptor: SerialDescriptor): ObjectSchema {
        val schema = generateSchema(descriptor, inlineRefs = INLINE_REFS)
        return schema as? ObjectSchema
            ?: error(
                "Tool schemas must be object schemas, but ${descriptor.serialName} produced " +
                    schema::class.simpleName,
            )
    }

    fun ObjectSchema.toInput(): Tool.Input =
        Tool.Input(properties = propertiesJson(), required = required.orEmpty())

    fun ObjectSchema.toOutput(): Tool.Output =
        Tool.Output(properties = propertiesJson(), required = required.orEmpty())

    /**
     * `Tool.Input`/`Tool.Output` carry `properties` and `required` and supply `"type": "object"`
     * themselves, so only the property map crosses over.
     */
    private fun ObjectSchema.propertiesJson(): JsonObject =
        json.encodeToJsonElement(ObjectSchema.serializer(), this)
            .jsonObject["properties"]
            ?.jsonObject
            ?: JsonObject(emptyMap())
}
