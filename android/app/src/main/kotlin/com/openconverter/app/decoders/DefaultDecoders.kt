package com.openconverter.app.decoders

import com.openconverter.app.decoders.kgg.KggKeyProvider
import com.openconverter.app.decoders.kgg.KggV5Decoder

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
    fun registry(kggKeys: KggKeyProvider): DecoderRegistry = DecoderRegistry(
        listOf(NcmDecoder, QmcDecoder, KgmDecoder, KggV5Decoder(kggKeys), KwmDecoder),
    )
}
