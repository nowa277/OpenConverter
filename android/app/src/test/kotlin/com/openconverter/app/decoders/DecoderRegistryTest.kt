package com.openconverter.app.decoders

import com.openconverter.app.decoders.kgg.KggKeyProvider
import com.openconverter.app.decoders.kgg.KggV5Decoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecoderRegistryTest {
    private class FakeA : Decoder {
        override val supportedExtensions = setOf(".a1", ".a2")
        override fun decrypt(input: ByteArray) = DecryptResult(input, "mp3")
    }
    private class FakeB : Decoder {
        override val supportedExtensions = setOf(".b1")
        override fun decrypt(input: ByteArray) = DecryptResult(input, "flac")
    }

    @Test fun find_by_extension_case_insensitive() {
        val r = DecoderRegistry(listOf(FakeA(), FakeB()))
        assertEquals("FakeA", r.find(".A1")!!.javaClass.simpleName)
        assertEquals("FakeB", r.find(".B1")!!.javaClass.simpleName)
    }

    @Test fun find_unknown_returns_null() {
        val r = DecoderRegistry(listOf(FakeA()))
        assertNull(r.find(".zzz"))
    }

    @Test fun supportedExtensions_union() {
        val r = DecoderRegistry(listOf(FakeA(), FakeB()))
        assertEquals(setOf(".a1", ".a2", ".b1"), r.supportedExtensions())
    }

    @Test fun empty_registry_finds_nothing() {
        val r = DecoderRegistry(emptyList())
        assertNull(r.find(".ncm"))
        assertEquals(emptySet<String>(), r.supportedExtensions())
    }

    @Test fun findForName_matches_compound_extension_case_insensitively() {
        val r = DecoderRegistry(listOf(FakeA()))

        val match = r.findForName("Track.A1.FLAC")

        assertEquals(".a1", match!!.encryptedExtension)
        assertEquals("FakeA", match.decoder.javaClass.simpleName)
    }

    @Test fun findForName_prefers_longest_extension_and_does_not_match_plain_names() {
        val short = object : Decoder {
            override val supportedExtensions = setOf(".kgm")
            override fun decrypt(input: ByteArray) = DecryptResult(input, "mp3")
        }
        val long = object : Decoder {
            override val supportedExtensions = setOf(".kgma")
            override fun decrypt(input: ByteArray) = DecryptResult(input, "flac")
        }
        val r = DecoderRegistry(listOf(short, long))

        assertEquals(".kgma", r.findForName("song.kgma")!!.encryptedExtension)
        assertNull(r.findForName("song.flac"))
    }

    @Test fun default_registry_injects_kgg_v5_without_replacing_legacy_kugou_decoder() {
        val r = DefaultDecoders.registry(KggKeyProvider { null })

        assertEquals(KggV5Decoder::class.java, r.find(".kgg")!!.javaClass)
        assertEquals(KgmDecoder, r.find(".kgm"))
        assertEquals(KgmDecoder, r.find(".kgma"))
        assertEquals(KgmDecoder, r.find(".vpr"))
    }
}
