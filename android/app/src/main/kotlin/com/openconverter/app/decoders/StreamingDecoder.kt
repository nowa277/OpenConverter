package com.openconverter.app.decoders

import java.io.InputStream
import java.io.OutputStream

data class StreamDecryptResult(
    val format: String,
    val tags: Map<String, String> = emptyMap(),
    val cover: ByteArray? = null,
    val musicId: String? = null,
    val rawMeta: String? = null,
)

interface StreamingDecoder : Decoder {
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): String

    fun decryptStreaming(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): StreamDecryptResult = StreamDecryptResult(format = decrypt(input, output, bufferSize))
}
