package com.openconverter.app.meta

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KrcDecoderTest {
    private val xorKey = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45,
        206.toByte(), 210.toByte(), 110, 105
    )

    private fun createSyntheticKrc(plainText: String): ByteArray {
        val deflated = ByteArrayOutputStream()
        DeflaterOutputStream(deflated).use { it.write(plainText.toByteArray(Charsets.UTF_8)) }
        val compressed = deflated.toByteArray()
        val xored = ByteArray(compressed.size)
        for (i in compressed.indices) {
            xored[i] = (compressed[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
        }
        val magic = "krc1".toByteArray(Charsets.US_ASCII)
        return magic + xored
    }

    @Test
    fun rejects_invalid_magic_or_too_short() {
        assertThrows(IllegalArgumentException::class.java) {
            KrcDecoder.decryptKrc("kr".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            KrcDecoder.decryptKrc("krc212345678".toByteArray())
        }
    }

    @Test
    fun decrypts_valid_krc_to_plaintext() {
        val plain = "[ti:Test Title]\n[ar:Artist]\n[1230,1000]<0,500,0>Hello <500,500,0>World"
        val krc = createSyntheticKrc(plain)
        val decrypted = KrcDecoder.decryptKrc(krc)
        assertEquals(plain, decrypted)
    }

    @Test
    fun fails_on_corrupted_payload() {
        val bad = "krc1".toByteArray() + "invalid compressed bytes".toByteArray()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            KrcDecoder.decryptKrc(bad)
        }
        assertTrue(ex.message!!.contains("KRC zlib 解压失败"))
    }

    @Test
    fun krcToLrc_converts_syllable_timestamps_and_filters_language_tag() {
        val krc = """
            [ti:Impossible]
            [ar:James Arthur]
            [al:Impossible]
            [language:eyJjb250ZW50IjpbXSwidmVyc2lvbiI6MX0=]
            [1230,2000]<0,1000,0>Hello <1000,1000,0>world
            [5000,3000]<0,1500,0>Second <1500,1500,0>line
        """.trimIndent()

        val lrc = KrcDecoder.krcToLrc(krc)!!
        assertFalse(lrc.contains("language:"))
        assertTrue(lrc.contains("[ti:Impossible]"))
        assertTrue(lrc.contains("[ar:James Arthur]"))
        assertTrue(lrc.contains("[00:01.23]Hello world"))
        assertTrue(lrc.contains("[00:05.00]Second line"))
    }

    @Test
    fun decodeKrcToLrc_end_to_end() {
        val krc = "[ti:Song]\n[ar:Singer]\n[0,1500]<0,750,0>One <750,750,0>Two"
        val bytes = createSyntheticKrc(krc)
        val lrc = KrcDecoder.decodeKrcToLrc(bytes)
        assertEquals("[ti:Song]\n[ar:Singer]\n[00:00.00]One Two", lrc)
    }

    @Test
    fun decrypts_real_kugou_krc_payload() {
        val realBase64 = "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"
        val bytes = java.util.Base64.getDecoder().decode(realBase64)
        val lrc = KrcDecoder.decodeKrcToLrc(bytes)
        assertTrue(lrc != null)
        assertTrue(lrc!!.contains("[ar:james Arthur]"))
        assertTrue(lrc.contains("[ti:Impossible]"))
        assertTrue(lrc.contains("I remember years ago"))
        assertTrue(lrc.contains("Impossible, impossible"))
        assertFalse(lrc.contains("language:"))
    }
}
