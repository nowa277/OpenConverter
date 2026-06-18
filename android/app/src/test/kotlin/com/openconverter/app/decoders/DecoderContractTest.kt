package com.openconverter.app.decoders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DecoderContractTest {

    private class StubDecoder : Decoder {
        override val supportedExtensions = setOf(".stub")
        override fun decrypt(input: ByteArray) =
            DecryptResult(audio = byteArrayOf(1, 2, 3), format = "mp3")
    }

    @Test fun decrypt_returns_audio_and_format() {
        val r = StubDecoder().decrypt(byteArrayOf(0))
        assertEquals(listOf<Byte>(1, 2, 3), r.audio.toList())
        assertEquals("mp3", r.format)
        assertNotNull(r.audio)
    }

    @Test fun decryptResult_meta_defaults_null() {
        val r = StubDecoder().decrypt(byteArrayOf(0))
        assertEquals(null, r.meta)
    }
}
