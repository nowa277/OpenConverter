package com.openconverter.app.decoders

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Generated PCM, fictitious tags and a 1x1 PNG; no downloaded music or private keys. */
object SyntheticNcm {
    const val META = """{"musicName":"Synthetic tone","artist":[["Synth",1],["Wave",2]],"album":"Regression fixtures","musicId":123456789,"format":"wav"}"""
    val cover: ByteArray = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
    val audio: ByteArray = ByteArrayOutputStream().apply {
        fun u16(n: Int) = write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(n.toShort()).array())
        fun u32(n: Int) = write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array())
        write("RIFF".toByteArray()); u32(36 + 16000); write("WAVEfmt ".toByteArray())
        u32(16); u16(1); u16(1); u32(8000); u32(16000); u16(2); u16(16)
        write("data".toByteArray()); u32(16000)
        repeat(8000) { u16((kotlin.math.sin(2 * Math.PI * 440 * it / 8000) * 12000).toInt()) }
    }.toByteArray()

    fun build(meta: String? = META, image: ByteArray = cover, imagePadding: Int = 7): ByteArray {
        fun aes(bytes: ByteArray, key: String): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), "AES"))
            doFinal(bytes)
        }
        val rc4Key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val keyBlock = aes("neteasecloudmusic".toByteArray() + rc4Key, "687A4852416D736F356B496E62617857").map { (it.toInt() xor 0x64).toByte() }.toByteArray()
        val metadata = meta?.let {
            ("163 key(Don't modify):" + Base64.getEncoder().encodeToString(aes("music:$it".toByteArray(), "2331346C6A6B5F215C5D2630553C2728")))
                .toByteArray().map { b -> (b.toInt() xor 0x63).toByte() }.toByteArray()
        } ?: byteArrayOf()
        val s = IntArray(256) { it }
        var j = 0
        for (i in s.indices) {
            j = (j + s[i] + rc4Key[i % rc4Key.size]) and 255
            val old = s[i]; s[i] = s[j]; s[j] = old
        }
        val stream = IntArray(256) { s[(s[it] + s[(it + s[it]) and 255]) and 255] }
        val encrypted = audio.mapIndexed { i, b -> (b.toInt() xor stream[(i + 1) % 256]).toByte() }.toByteArray()
        return ByteArrayOutputStream().apply {
            fun u32(n: Int) = write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array())
            write("CTENFDAM".toByteArray()); write(ByteArray(2)); u32(keyBlock.size); write(keyBlock)
            u32(metadata.size); write(metadata); write(ByteArray(5)); u32(image.size + imagePadding); u32(image.size)
            write(image); write(ByteArray(imagePadding)); write(encrypted)
        }.toByteArray()
    }
}
