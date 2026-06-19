package com.openconverter.app.decoders

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
}
