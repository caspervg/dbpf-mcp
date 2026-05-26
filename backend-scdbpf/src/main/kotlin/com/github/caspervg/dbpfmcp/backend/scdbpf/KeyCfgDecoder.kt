package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.KeyCfgModel
import com.github.caspervg.dbpfmcp.core.KeyCfgRecord
import com.github.caspervg.dbpfmcp.core.KeyCfgTextFragment

private val messageIdRegex = Regex("""(?:0x)?[0-9A-F]{7,8}""")
private val quotedTextRegex = Regex(""""([^"]+)"""")
private val keyCombinationRegex = Regex(
    """(?i)\b(?:Control|Ctrl|Shift|Alt|F\d{1,2}|Numpad[0-9/*+\-]|PageUp|PageDown|Home|End|Left|Right|Up|Down|Escape|Space|Return|Enter|Tab|Pause|Break|ScrollLock|NumLock|CapsLock|Backspace|Insert|Delete|[A-Z0-9\[\]`~_\-=+\\|;:',<.>/?])(?:\s+(?:Control|Ctrl|Shift|Alt|up|down|F\d{1,2}|Numpad[0-9/*+\-]|PageUp|PageDown|Home|End|Left|Right|Up|Down|Escape|Space|Return|Enter|Tab|Pause|Break|ScrollLock|NumLock|CapsLock|Backspace|Insert|Delete|[A-Z0-9\[\]`~_\-=+\\|;:',<.>/?]))*\b"""
)

internal fun decodeKeyCfgPayload(
    bytes: ByteArray,
    compressed: Boolean,
    tgi: com.github.caspervg.dbpfmcp.core.Tgi,
): KeyCfgModel {
    val fragments = extractTextFragments(bytes)
    val records = extractRecords(fragments)
    val notes = buildList {
        add("Heuristic decoder: this format appears text-centric with binary separators.")
        if (records.isEmpty()) {
            add("No fully reconstructed key records were found; inspect textFragments for embedded labels and message IDs.")
        }
        if (fragments.any { it.text.contains("KeyConfig", ignoreCase = true) }) {
            add("Embedded text references SimCity 4 KeyConfig resources, which supports a KEYCFG/TAB interpretation.")
        }
    }

    return KeyCfgModel(
        tgi = tgi,
        compressed = compressed,
        size = bytes.size,
        formatHint = "Heuristic KEYCFG/TAB-like text resource",
        textFragments = fragments,
        records = records,
        notes = notes,
    )
}

private fun extractTextFragments(bytes: ByteArray): List<KeyCfgTextFragment> {
    val fragments = mutableListOf<KeyCfgTextFragment>()
    var start = -1
    val buffer = StringBuilder()

    fun flush(endExclusive: Int) {
        if (start >= 0) {
            val text = buffer.toString()
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (text.length >= 4) {
                fragments += KeyCfgTextFragment(start, text)
            }
        }
        start = -1
        buffer.setLength(0)
    }

    bytes.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xFF
        val char = unsigned.toChar()
        val printable = unsigned in 32..126
        if (printable) {
            if (start < 0) start = index
            buffer.append(char)
        } else if ((char == '\n' || char == '\r' || char == '\t') && start >= 0) {
            buffer.append(' ')
        } else {
            flush(index)
        }
    }
    flush(bytes.size)
    return fragments
}

private fun extractRecords(fragments: List<KeyCfgTextFragment>): List<KeyCfgRecord> {
    if (fragments.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<KeyCfgTextFragment>>()
    for (fragment in fragments) {
        val current = groups.lastOrNull()
        if (current == null) {
            groups += mutableListOf(fragment)
            continue
        }
        val previous = current.last()
        val closeEnough = fragment.offset - previous.offset <= maxOf(previous.text.length + 8, 20)
        val looksRelated = messageIdRegex.containsMatchIn(fragment.text) ||
            messageIdRegex.containsMatchIn(previous.text) ||
            fragment.text.contains('"') ||
            previous.text.contains('"')
        if (closeEnough && looksRelated) {
            current += fragment
        } else {
            groups += mutableListOf(fragment)
        }
    }

    return groups.mapNotNull { group ->
        val rawSegments = group.map(KeyCfgTextFragment::text)
        val combined = rawSegments.joinToString(" ")
        val messageIds = messageIdRegex.findAll(combined)
            .map { match ->
                match.value.removePrefix("0x").uppercase().padStart(8, '0')
            }
            .distinct()
            .toList()
        val description = quotedTextRegex.find(combined)?.groupValues?.get(1)
        val keyCombination = keyCombinationRegex.find(combined)
            ?.value
            ?.trim()
            ?.takeUnless { it.equals(description, ignoreCase = true) }

        if (messageIds.isEmpty() && description == null) {
            null
        } else {
            KeyCfgRecord(
                offset = group.first().offset,
                rawSegments = rawSegments,
                keyCombination = keyCombination,
                messageIds = messageIds,
                description = description,
            )
        }
    }
}
