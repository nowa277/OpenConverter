package com.openconverter.app.decoders.kgg

import com.openconverter.app.decoders.DecryptResult
import com.openconverter.app.decoders.FormatSniffer
import com.openconverter.app.decoders.StreamingDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class KggV5Decoder(
    private val keyProvider: KggKeyProvider,
    private val ekeyUnwrapper: (String) -> ByteArray = KggEkey::unwrap,
) : StreamingDecoder {
    override val supportedExtensions: Set<String> = setOf(".kgg")

    override fun decrypt(input: ByteArray): DecryptResult {
        val output = ByteArrayOutputStream()
        val format = decrypt(ByteArrayInputStream(input), output)
        return DecryptResult(output.toByteArray(), format)
    }

    override fun decrypt(input: InputStream, output: OutputStream, bufferSize: Int): String {
        require(bufferSize > 0) { "KGG buffer size must be positive" }
        val prefix = readPrefix(input)
        val header = KggHeaderParser.parse(prefix)
        require(header.cryptoVersion == 5) {
            "KGG crypto version ${header.cryptoVersion} belongs to the legacy decoder"
        }

        val encodedKey = keyProvider.find(header.encryptionKeyId) ?: when (keyProvider.count()) {
            0 -> throw IllegalArgumentException(
                "No KGG keys imported; import KGMusicV3.db or kgg.key in Settings",
            )
            else -> throw IllegalArgumentException(
                "Missing KGG key for ${header.encryptionKeyId}; import a database or key file containing this ID",
            )
        }
        val cipher = KggQmc2.cipher(ekeyUnwrapper(encodedKey))

        val probe = ByteArrayOutputStream(PROBE_SIZE)
        var format: String? = null
        var payloadOffset = 0L

        fun emit(buffer: ByteArray, length: Int) {
            if (length == 0) return
            cipher.apply(buffer, length, payloadOffset)
            payloadOffset += length

            var consumed = 0
            if (format == null) {
                val probeLength = minOf(length, PROBE_SIZE - probe.size())
                probe.write(buffer, 0, probeLength)
                consumed = probeLength
                if (probe.size() == PROBE_SIZE) {
                    format = detectFormat(probe.toByteArray())
                    output.write(probe.toByteArray())
                }
            }
            if (format != null && consumed < length) output.write(buffer, consumed, length - consumed)
        }

        if (header.headerLength < prefix.size) {
            val initialPayload = prefix.copyOfRange(header.headerLength, prefix.size)
            emit(initialPayload, initialPayload.size)
        }

        val buffer = ByteArray(bufferSize)
        while (true) {
            val length = input.read(buffer)
            if (length < 0) break
            if (length == 0) continue
            emit(buffer, length)
        }

        if (format == null) {
            format = detectFormat(probe.toByteArray())
            output.write(probe.toByteArray())
        }
        return requireNotNull(format)
    }

    private fun readPrefix(input: InputStream): ByteArray {
        val prefix = ByteArray(KggHeaderParser.PREFIX_SIZE)
        var count = 0
        while (count < prefix.size) {
            val read = input.read(prefix, count, prefix.size - count)
            if (read < 0) break
            if (read == 0) continue
            count += read
        }
        require(count >= 0x48) { "KGG header is truncated" }
        return if (count == prefix.size) prefix else prefix.copyOf(count)
    }

    private fun detectFormat(probe: ByteArray): String {
        require(probe.size >= 4) { "KGG decrypted audio is too short" }
        val format = FormatSniffer.sniff(probe)
        val knownMp3 = probe.startsWith("ID3".toByteArray()) ||
            (probe[0].toInt() and 0xff == 0xff && probe[1].toInt() and 0xe0 == 0xe0)
        require(format != "mp3" || knownMp3) { "KGG decrypted audio format is unknown" }
        return format
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        private const val PROBE_SIZE = 16
    }
}
