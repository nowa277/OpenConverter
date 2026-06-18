package com.openconverter.app.decoders

/**
 * Routes a file extension to its [Decoder]. Construction is explicit so tests
 * can inject fakes; the engine receives a registry and is agnostic to which
 * decoders are registered. v2 adds QMCv2 by appending a decoder here only.
 */
class DecoderRegistry(decoders: List<Decoder>) {
    private val byExt: Map<String, Decoder> = buildMap {
        decoders.forEach { d -> d.supportedExtensions.forEach { put(it.lowercase(), d) } }
    }

    /** @param ext with leading dot, any case. Returns null if unsupported. */
    fun find(ext: String): Decoder? = byExt[ext.lowercase()]

    fun supportedExtensions(): Set<String> = byExt.keys.toSet()

    companion object {
        /** Populated by [DefaultDecoders] in Milestone 3. Empty here = nothing wired yet. */
        val DEFAULT: DecoderRegistry = DecoderRegistry(emptyList())
    }
}
