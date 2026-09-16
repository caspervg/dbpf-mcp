package com.github.caspervg.dbpfmcp.backend.scdbpf

internal object QfsDecoder {
    private const val MAGIC = 0x10FB

    data class Result(
        val bytes: ByteArray,
        val declaredSize: Int,
        val extendedHeader: Boolean,
    )

    fun isQfsCompressed(bytes: ByteArray, offset: Int = 0): Boolean =
        bytes.size >= offset + 2 &&
            ((((bytes[offset].toInt() and 0xFF) and 0xFE) shl 8) or (bytes[offset + 1].toInt() and 0xFF)) == MAGIC

    fun decode(bytes: ByteArray, offset: Int = 0): Result? {
        if (!isQfsCompressed(bytes, offset) || bytes.size < offset + 5) return null

        val first = bytes[offset].toInt() and 0xFF
        val declaredSize = ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 8) or
            (bytes[offset + 4].toInt() and 0xFF)
        var position = offset + if ((first and 0x01) != 0) 8 else 5
        if (position > bytes.size) return null

        val output = ByteArray(declaredSize)
        var outputPosition = 0

        while (true) {
            if (position >= bytes.size) return null
            val c0 = bytes[position++].toInt() and 0xFF

            if (c0 >= 0xFC) {
                val literalCount = c0 and 0x03
                if (!canReadWrite(bytes, position, outputPosition, literalCount, output.size)) return null
                copyLiteral(bytes, position, output, outputPosition, literalCount)
                outputPosition += literalCount
                return if (outputPosition == output.size) {
                    Result(output, declaredSize, extendedHeader = (first and 0x01) != 0)
                } else {
                    null
                }
            }

            val literalCount: Int
            val copyLength: Int
            val copyOffset: Int
            when {
                c0 <= 0x7F -> {
                    if (position >= bytes.size) return null
                    val c1 = bytes[position++].toInt() and 0xFF
                    literalCount = c0 and 0x03
                    copyLength = ((c0 shr 2) and 0x07) + 3
                    copyOffset = ((c0 and 0x60) shl 3) + c1 + 1
                }
                c0 <= 0xBF -> {
                    if (position + 1 >= bytes.size) return null
                    val c1 = bytes[position++].toInt() and 0xFF
                    val c2 = bytes[position++].toInt() and 0xFF
                    literalCount = (c1 shr 6) and 0x03
                    copyLength = (c0 and 0x3F) + 4
                    copyOffset = ((c1 and 0x3F) shl 8) + c2 + 1
                }
                c0 <= 0xDF -> {
                    if (position + 2 >= bytes.size) return null
                    val c1 = bytes[position++].toInt() and 0xFF
                    val c2 = bytes[position++].toInt() and 0xFF
                    val c3 = bytes[position++].toInt() and 0xFF
                    literalCount = c0 and 0x03
                    copyLength = ((c0 and 0x0C) shl 6) + c3 + 5
                    copyOffset = ((c0 and 0x10) shl 12) + (c1 shl 8) + c2 + 1
                }
                else -> {
                    literalCount = ((c0 and 0x1F) shl 2) + 4
                    if (!canReadWrite(bytes, position, outputPosition, literalCount, output.size)) return null
                    copyLiteral(bytes, position, output, outputPosition, literalCount)
                    position += literalCount
                    outputPosition += literalCount
                    continue
                }
            }

            if (position + literalCount > bytes.size) return null
            if (copyOffset > outputPosition + literalCount || outputPosition + literalCount + copyLength > output.size) {
                return null
            }
            if (literalCount > 0) {
                copyLiteral(bytes, position, output, outputPosition, literalCount)
                position += literalCount
                outputPosition += literalCount
            }
            val source = outputPosition - copyOffset
            for (index in 0 until copyLength) {
                output[outputPosition + index] = output[source + index]
            }
            outputPosition += copyLength
        }
    }

    private fun canReadWrite(
        input: ByteArray,
        inputPosition: Int,
        outputPosition: Int,
        length: Int,
        outputSize: Int,
    ): Boolean =
        inputPosition + length <= input.size && outputPosition + length <= outputSize

    private fun copyLiteral(
        input: ByteArray,
        inputPosition: Int,
        output: ByteArray,
        outputPosition: Int,
        length: Int,
    ) {
        if (length > 0) {
            input.copyInto(output, destinationOffset = outputPosition, startIndex = inputPosition, endIndex = inputPosition + length)
        }
    }
}
