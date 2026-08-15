package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.Tgi
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyCfgDecoderTest {
    @Test
    fun `decoder extracts candidate records from plop and paint sample`() {
        val bytes = Base64.getDecoder().decode(
            "7wAAABD7AAEe6jsgQWRkaXRvbmFsIGtleWJvYXJkIHNob3J0Y3V0cyBmb3IgdGhlIA0KOyBTCgtlZegzRCBWaWV3IEtleUNvbmZpZyBmaWxlIGluIFNpbUNpdHlfMS4bO2RhdAAHB0FtYXTmZG9jdW1ldGF0aW9uLCBUR0kgMHhhMmUzZDUzMwELLOE2YTIzMWVhYQwL4jkzNjJlZi4NCg0KT45AACABJD3iOUYyMUMzQTEgIlRvAoRnZ+NMb3RQbG9wIFdpbmRvdyINk4A5CjDhOEI0QTdGMkUYOeFQcm9wUGFpbh89dGVy/A==",
        )

        val model = decodeKeyCfgPayload(
            bytes = bytes,
            compressed = true,
            tgi = Tgi(0xA2E3D533, 0x8F1E6D69, 0x5CBCFBF8),
        )

        assertEquals("Heuristic KEYCFG/TAB-like text resource", model.formatHint)
        assertTrue(model.textFragments.any { it.text.contains("keyboard shortcuts", ignoreCase = true) })
        assertTrue(model.textFragments.any { it.text.contains("KeyConfig", ignoreCase = true) })
        assertTrue(model.records.any { it.description?.contains("LotPlop Window") == true })
        assertTrue(model.records.any { "8B4A7F2E" in it.messageIds })
    }
}
