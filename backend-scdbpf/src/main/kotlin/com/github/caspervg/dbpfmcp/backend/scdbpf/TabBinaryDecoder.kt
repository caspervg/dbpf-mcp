package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.TabBinaryChunk
import com.github.caspervg.dbpfmcp.core.TabBinaryModel
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun decodeTabBinaryPayload(
    bytes: ByteArray,
    compressed: Boolean,
    tgi: Tgi,
    maxWords: Int,
): TabBinaryModel {
    val wordCount = minOf(bytes.size / 4, maxWords)
    val words = mutableListOf<String>()
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    repeat(wordCount) {
        words += formatHex32(buffer.int.toLong() and 0xFFFF_FFFFL)
    }

    val chunkSizeWords = 4
    val chunks = words.chunked(chunkSizeWords).take(16).mapIndexed { index, chunk ->
        TabBinaryChunk(
            offset = index * chunkSizeWords * 4,
            words = chunk,
        )
    }

    val notes = buildList {
        add("Structured binary TAB probe: words are interpreted as little-endian 32-bit values.")
        if (words.isNotEmpty() && words.first() == formatHex32(bytes.size.toLong())) {
            add("First word matches decompressed byte size, which may indicate an internal size/header field.")
        }
        add("This is not yet a semantic TAB decoder; it is a structural probe for compiled accelerator tables.")
    }

    return TabBinaryModel(
        tgi = tgi,
        compressed = compressed,
        size = bytes.size,
        formatHint = "Structured binary TAB probe",
        headerWords = words.take(8),
        words = words,
        chunks = chunks,
        notes = notes,
    )
}
