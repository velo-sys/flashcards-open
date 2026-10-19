package de.flashcards.open.domain

import de.flashcards.open.domain.text.DetectedEncoding
import de.flashcards.open.domain.text.TextDecoding
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDecodingTest {
    // Deliberately non-ASCII: these fixtures are what the encoding detection is tested against.
    private val sample = "Größe – € „Test“ ß 🎉"
    private val ansiSample = "Größe – € „Test“ ß" // Windows-1252 has no emoji

    @Test fun utf8() {
        val d = TextDecoding.decode(sample.toByteArray(Charsets.UTF_8))
        assertEquals(DetectedEncoding.UTF8, d.encoding)
        assertEquals(sample, d.text)
    }

    @Test fun utf8Bom() {
        val d = TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sample.toByteArray(Charsets.UTF_8))
        assertEquals(DetectedEncoding.UTF8_BOM, d.encoding)
        assertEquals(sample, d.text)
    }

    @Test fun windows1252Fallback() {
        val d = TextDecoding.decode(ansiSample.toByteArray(charset("windows-1252")))
        assertEquals(DetectedEncoding.WINDOWS_1252, d.encoding)
        assertEquals(ansiSample, d.text)
    }

    @Test fun utf16LeAndBe() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + sample.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + sample.toByteArray(Charsets.UTF_16BE)
        assertEquals(sample, TextDecoding.decode(le).text)
        assertEquals(DetectedEncoding.UTF16_LE, TextDecoding.decode(le).encoding)
        assertEquals(sample, TextDecoding.decode(be).text)
        assertEquals(DetectedEncoding.UTF16_BE, TextDecoding.decode(be).encoding)
    }

    @Test fun normalizesToNfc() {
        val decomposed = "über" // u + combining diaeresis instead of ü
        assertEquals("über", TextDecoding.decode(decomposed.toByteArray(Charsets.UTF_8)).text)
    }
}
