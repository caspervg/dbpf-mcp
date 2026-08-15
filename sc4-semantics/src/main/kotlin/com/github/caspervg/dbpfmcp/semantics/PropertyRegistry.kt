package com.github.caspervg.dbpfmcp.semantics

import com.fleeksoft.ksoup.Ksoup
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class PropertyDefinition(
    val id: Long,
    val name: String,
    val type: String?,
    val description: String?,
    val group: String?,
)

class PropertyRegistry internal constructor(
    private val propertiesById: Map<Long, PropertyDefinition>,
) {
    fun propertyById(id: Long): PropertyDefinition? = propertiesById[id]

    val size: Int get() = propertiesById.size

    companion object {
        private const val RESOURCE_NAME = "/tropod_Properties.xml"

        val instance: PropertyRegistry by lazy {
            val stream = checkNotNull(PropertyRegistry::class.java.getResourceAsStream(RESOURCE_NAME)) {
                "Missing bundled resource $RESOURCE_NAME"
            }
            stream.use(::loadPropertyRegistry)
        }
    }
}

fun loadPropertyRegistry(input: InputStream): PropertyRegistry {
    val text = input.readBytes().toString(StandardCharsets.UTF_8)
    val document = Ksoup.parse(text)
    val properties = linkedMapOf<Long, PropertyDefinition>()

    for (group in document.select("group")) {
        val groupName = group.attr("name").ifBlank { null }
        for (property in group.select("property")) {
            val idText = property.attr("num")
            if (idText.isBlank()) continue
            val id = parseHexId(idText, "num")
            properties[id] = PropertyDefinition(
                id = id,
                name = property.attr("name").ifBlank { "Unnamed Property" },
                type = canonicalPropertyType(property.attr("type").ifBlank { null }),
                description = property.attr("desc").ifBlank { null },
                group = groupName,
            )
        }
    }

    return PropertyRegistry(properties)
}

