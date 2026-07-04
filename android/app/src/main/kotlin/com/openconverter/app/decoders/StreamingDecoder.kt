package com.openconverter.app.decoders

import java.io.InputStream
import java.io.OutputStream

interface StreamingDecoder : Decoder {
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): String
}
