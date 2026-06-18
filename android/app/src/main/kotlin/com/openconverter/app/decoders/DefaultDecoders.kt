package com.openconverter.app.decoders

/**
 * The default registry wired with all v1 decoders. The engine resolves a file
 * to a [Decoder] by extension; missing extensions fall through to plaintext.
 *
 * Registered (v1):
 *   .ncm                              → NcmDecoder
 *   .qmc0/.qmc3/.qmcflac/.qmcogg      → QmcDecoder (v1 only)
 *   .kgm/.kgma/.vpr                   → KgmDecoder
 *   .kwm                              → KwmDecoder
 *
 * v2+ adds: .mflac/.mgg/.bkc (QmcDecoder v2 path with ekey UI).
 */
object DefaultDecoders {
    val registry: DecoderRegistry = DecoderRegistry(
        listOf(NcmDecoder, QmcDecoder, KgmDecoder, KwmDecoder)
    )
}
