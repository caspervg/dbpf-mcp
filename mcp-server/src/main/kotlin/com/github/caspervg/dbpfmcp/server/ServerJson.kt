package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.InputError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * The server's JSON codecs.
 *
 * Results are encoded straight from the domain models, all of which are `@Serializable`; the
 * hexadecimal identifier fields carry their own serializers (see `core-api`'s `Hex.kt`), so TGI
 * components and property IDs stay in the form SC4 tooling uses.
 */
object ServerJson {

    /**
     * Encoding configuration.
     *
     * `explicitNulls = false` omits absent optional fields instead of writing `"field": null` for
     * each one. `encodeDefaults = true` keeps everything else — an empty `warnings` array is still
     * written, so the shape of a populated response is unchanged.
     */
    val encode: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Tolerant of unknown fields so a client sending extra arguments is not rejected outright. */
    val decode: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    inline fun <reified T> encodeResult(value: T): JsonObject =
        encode.encodeToJsonElement(value).jsonObject

    /**
     * Decodes tool arguments into [T].
     *
     * A shape violation becomes an [InputError] so the caller is told which field is wrong;
     * kotlinx's own [SerializationException] would otherwise surface through the generic
     * "unexpected error" path with no indication that the request was at fault.
     */
    inline fun <reified T> decodeArguments(arguments: JsonElement): T = try {
        decode.decodeFromJsonElement<T>(arguments)
    } catch (exception: SerializationException) {
        throw InputError(describeDecodeFailure(exception), exception)
    }

    fun describeDecodeFailure(exception: SerializationException): String {
        val detail = exception.message?.substringBefore('\n')?.trim().orEmpty()
        return if (detail.isEmpty()) "Invalid tool arguments" else "Invalid tool arguments: $detail"
    }
}
