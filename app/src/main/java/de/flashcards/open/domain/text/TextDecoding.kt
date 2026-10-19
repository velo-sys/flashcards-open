package de.flashcards.open.domain.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

enum class DetectedEncoding {
    UTF8_BOM, UTF16_LE, UTF16_BE, UTF8, WINDOWS_1252,
}

data class DecodedText(val text: String, val encoding: DetectedEncoding)

/**
 * Detects the encoding like the reference prototype: BOM → strict UTF-8 → Windows-1252 (Excel
 * ANSI), followed by Unicode NFC normalisation.
 */
object TextDecoding {
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    fun decode(bytes: ByteArray): DecodedText {
        val (raw, encoding) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to DetectedEncoding.UTF8_BOM
            bytes.startsWith(0xFF, 0xFE) ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to DetectedEncoding.UTF16_LE
            bytes.startsWith(0xFE, 0xFF) ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to DetectedEncoding.UTF16_BE
            else -> decodeStrictUtf8(bytes)?.let { it to DetectedEncoding.UTF8 }
                ?: (String(bytes, WINDOWS_1252) to DetectedEncoding.WINDOWS_1252)
        }
        return DecodedText(nfc(raw), encoding)
    }

    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun decodeStrictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }
}
