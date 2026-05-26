package com.github.caspervg.dbpfmcp.semantics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropertyRegistryTest {
    @Test
    fun `bundled tropod registry loads expected properties`() {
        val registry = PropertyRegistry.instance

        assertTrue(registry.size > 1000)

        val exemplarName = registry.propertyById(0x00000020)
        assertNotNull(exemplarName)
        assertEquals("Exemplar Name", exemplarName.name)
        assertEquals("String", exemplarName.type)

        val bulldozeCost = registry.propertyById(0x099AFACD)
        assertNotNull(bulldozeCost)
        assertEquals("Bulldoze Cost", bulldozeCost.name)
    }
}
