package com.openconverter.app.decoders

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tan

object QmcDecoder : Decoder {

    override val supportedExtensions: Set<String> = setOf(
        ".qmc0", ".qmc3", ".qmcflac", ".qmcogg", ".qmc1", ".qmc2", ".tkm",
        ".mflac", ".mflac0", ".mflac2", ".mflac4", ".mgg", ".mgg1", ".mgg2", ".mgg4", ".mggl",
        ".bkc", ".bkcmp3", ".bkcflac", ".bkcogg", ".bkcm4a", ".bkcwav", ".bkcwma", ".bkcape"
    )

    private val QMC1_STATIC_BOX = intArrayOf(
        0x77, 0x48, 0x32, 0x73, 0xDE, 0xF2, 0xC0, 0xC8, 0x95, 0xEC, 0x30, 0xB2, 0x51, 0xC3, 0xE1, 0xA0,
        0x9E, 0xE6, 0x9D, 0xCF, 0xFA, 0x7F, 0x14, 0xD1, 0xCE, 0xB8, 0xDC, 0xC3, 0x4A, 0x67, 0x93, 0xD6,
        0x28, 0xC2, 0x91, 0x70, 0xCA, 0x8D, 0xA2, 0xA4, 0xF0, 0x08, 0x61, 0x90, 0x7E, 0x6F, 0xA2, 0xE0,
        0xEB, 0xAE, 0x3E, 0xB6, 0x67, 0xC7, 0x92, 0xF4, 0x91, 0xB5, 0xF6, 0x6C, 0x5E, 0x84, 0x40, 0xF7,
        0xF3, 0x1B, 0x02, 0x7F, 0xD5, 0xAB, 0x41, 0x89, 0x28, 0xF4, 0x25, 0xCC, 0x52, 0x11, 0xAD, 0x43,
        0x68, 0xA6, 0x41, 0x8B, 0x84, 0xB5, 0xFF, 0x2C, 0x92, 0x4A, 0x26, 0xD8, 0x47, 0x6A, 0x7C, 0x95,
        0x61, 0xCC, 0xE6, 0xCB, 0xBB, 0x3F, 0x47, 0x58, 0x89, 0x75, 0xC3, 0x75, 0xA1, 0xD9, 0xAF, 0xCC,
        0x08, 0x73, 0x17, 0xDC, 0xAA, 0x9A, 0xA2, 0x16, 0x41, 0xD8, 0xA2, 0x06, 0xC6, 0x8B, 0xFC, 0x66,
        0x34, 0x9F, 0xCF, 0x18, 0x23, 0xA0, 0x0A, 0x74, 0xE7, 0x2B, 0x27, 0x70, 0x92, 0xE9, 0xAF, 0x37,
        0xE6, 0x8C, 0xA7, 0xBC, 0x62, 0x65, 0x9C, 0xC2, 0x08, 0xC9, 0x88, 0xB3, 0xF3, 0x43, 0xAC, 0x74,
        0x2C, 0x0F, 0xD4, 0xAF, 0xA1, 0xC3, 0x01, 0x64, 0x95, 0x4E, 0x48, 0x9F, 0xF4, 0x35, 0x78, 0x95,
        0x7A, 0x39, 0xD6, 0x6A, 0xA0, 0x6D, 0x40, 0xE8, 0x4F, 0xA8, 0xEF, 0x11, 0x1D, 0xF3, 0x1B, 0x3F,
        0x3F, 0x07, 0xDD, 0x6F, 0x5B, 0x19, 0x30, 0x19, 0xFB, 0xEF, 0x0E, 0x37, 0xF0, 0x0E, 0xCD, 0x16,
        0x49, 0xFE, 0x53, 0x47, 0x13, 0x1A, 0xBD, 0xA4, 0xF1, 0x40, 0x19, 0x60, 0x0E, 0xED, 0x68, 0x09,
        0x06, 0x5F, 0x4D, 0xCF, 0x3D, 0x1A, 0xFE, 0x20, 0x77, 0xE4, 0xD9, 0xDA, 0xF9, 0xA4, 0x2B, 0x76,
        0x1C, 0x71, 0xDB, 0x00, 0xBC, 0xFD, 0x0C, 0x6C, 0xA5, 0x47, 0xF7, 0xF6, 0x00, 0x79, 0x4A, 0x11
    )

    private val V1_MASK = ByteArray(32768).apply {
        for (i in 0 until 32768) {
            this[i] = QMC1_STATIC_BOX[(i * i + 27) and 0xff].toByte()
        }
    }

    private object Base64Decoder {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private val INV = IntArray(256) { -1 }.apply {
            for (i in ALPHABET.indices) this[ALPHABET[i].code] = i
            this['='.code] = 0
        }
        fun decode(str: String): ByteArray {
            val clean = str.filter { !it.isWhitespace() }
            if (clean.isEmpty()) return ByteArray(0)
            var pad = 0
            if (clean.endsWith("==")) pad = 2
            else if (clean.endsWith("=")) pad = 1
            val numBytes = (clean.length * 6 / 8) - pad
            val out = ByteArray(numBytes)
            var outIdx = 0
            var i = 0
            while (i < clean.length) {
                val c0 = INV[clean[i].code]
                val c1 = INV[clean[i + 1].code]
                val c2 = INV[clean[i + 2].code]
                val c3 = INV[clean[i + 3].code]
                val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
                if (outIdx < numBytes) out[outIdx++] = (triple ushr 16).toByte()
                if (outIdx < numBytes) out[outIdx++] = (triple ushr 8).toByte()
                if (outIdx < numBytes) out[outIdx++] = triple.toByte()
                i += 4
            }
            return out
        }
    }

    private data class DetectedKey(val ekey: ByteArray, val audioLen: Int)

    private fun detectKey(buf: ByteArray): DetectedKey? {
        val len = buf.size
        if (len >= 8) {
            val tail = buf.sliceArray(len - 8 until len).decodeToString()
            if (tail == "musicex\u0000") {
                throw IllegalArgumentException("This file was encrypted with a newer QQ Music client (musicex) without an embedded key. Please downgrade your client or provide a key database.")
            }
        }
        if (len >= 4) {
            val tail4 = buf.sliceArray(len - 4 until len).decodeToString()
            if (tail4 == "STag") {
                throw IllegalArgumentException("This file contains an STag but no embedded key. Please downgrade your QQ Music client or provide a key database.")
            }
        }
        if (len < 8) return null
        if (len >= 0x18) {
            try {
                val head = buf.sliceArray(0 until 4).decodeToString()
                if (head == "STag") {
                    val ekeyLen = ByteBuffer.wrap(buf, 0x14, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (ekeyLen in 1 until 0xFFFF) {
                        val ekeyEnd = 0x18 + ekeyLen
                        if (ekeyEnd < len) {
                            val ekeyBase64 = buf.sliceArray(0x18 until ekeyEnd).decodeToString().trim()
                            if (ekeyBase64.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it.isWhitespace() }) {
                                val ekeyBytes = Base64Decoder.decode(ekeyBase64)
                                return DetectedKey(ekeyBytes, ekeyEnd)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {}
        }
        try {
            val qTagBytes = buf.sliceArray(len - 4 until len)
            if (qTagBytes.decodeToString() == "QTag") {
                val metaLen = ByteBuffer.wrap(buf, len - 8, 4).order(ByteOrder.BIG_ENDIAN).int
                if (metaLen > 0 && metaLen < len - 8) {
                    val rawMeta = buf.sliceArray(len - 8 - metaLen until len - 8).decodeToString()
                    val parts = rawMeta.split(',')
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val ekeyBytes = Base64Decoder.decode(parts[0])
                        return DetectedKey(ekeyBytes, len - 8 - metaLen)
                    }
                }
            }
        } catch (t: Throwable) {}
        try {
            val keyLen = ByteBuffer.wrap(buf, len - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (keyLen in 1 until 0xFFFF && keyLen < len - 4) {
                val ekeyBase64 = buf.sliceArray(len - 4 - keyLen until len - 4).decodeToString().trim()
                if (ekeyBase64.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it.isWhitespace() }) {
                    val ekeyBytes = Base64Decoder.decode(ekeyBase64)
                    return DetectedKey(ekeyBytes, len - 4 - keyLen)
                }
            }
        } catch (t: Throwable) {}
        return null
    }

    private class TeaCipher(key: ByteArray, val rounds: Int = 64) {
        val k0: Int
        val k1: Int
        val k2: Int
        val k3: Int
        init {
            require(key.size == 16)
            require(rounds % 2 == 0)
            val buf = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
            k0 = buf.int
            k1 = buf.int
            k2 = buf.int
            k3 = buf.int
        }
        fun decryptBlock(dst: ByteArray, dstOffset: Int, src: ByteArray, srcOffset: Int) {
            val srcBuf = ByteBuffer.wrap(src, srcOffset, 8).order(ByteOrder.LITTLE_ENDIAN)
            var v0 = srcBuf.int
            var v1 = srcBuf.int
            val delta = 0x9e3779b9.toInt()
            var sum = (delta * (rounds / 2))
            for (i in 0 until rounds / 2) {
                v1 -= ((v0 shl 4) + k2) xor (v0 + sum) xor ((v0 ushr 5) + k3)
                v0 -= ((v1 shl 4) + k0) xor (v1 + sum) xor ((v1 ushr 5) + k1)
                sum -= delta
            }
            val dstBuf = ByteBuffer.wrap(dst, dstOffset, 8).order(ByteOrder.LITTLE_ENDIAN)
            dstBuf.putInt(v0)
            dstBuf.putInt(v1)
        }
    }

    private fun decryptTencentTea(inBuf: ByteArray, key: ByteArray): ByteArray {
        require(inBuf.size % 8 == 0 && inBuf.size >= 16)
        val blk = TeaCipher(key, 32)
        val tmpBuf = ByteArray(8)
        blk.decryptBlock(tmpBuf, 0, inBuf, 0)
        val nPadLen = tmpBuf[0].toInt() and 0x7
        val saltLen = 2
        val zeroLen = 7
        val outLen = inBuf.size - 1 - nPadLen - saltLen - zeroLen
        require(outLen >= 0)
        val outBuf = ByteArray(outLen)
        var ivPrev = ByteArray(8)
        val ivCur = inBuf.sliceArray(0 until 8)
        var inBufPos = 8
        var tmpIdx = 1 + nPadLen
        
        fun cryptBlock() {
            System.arraycopy(ivCur, 0, ivPrev, 0, 8)
            System.arraycopy(inBuf, inBufPos, ivCur, 0, 8)
            for (j in 0 until 8) tmpBuf[j] = (tmpBuf[j].toInt() xor ivCur[j].toInt()).toByte()
            blk.decryptBlock(tmpBuf, 0, tmpBuf, 0)
            inBufPos += 8
            tmpIdx = 0
        }
        
        var i = 1
        while (i <= saltLen) {
            if (tmpIdx < 8) { tmpIdx++; i++ } else cryptBlock()
        }
        
        var outBufPos = 0
        while (outBufPos < outLen) {
            if (tmpIdx < 8) {
                outBuf[outBufPos++] = (tmpBuf[tmpIdx].toInt() xor ivPrev[tmpIdx].toInt()).toByte()
                tmpIdx++
            } else cryptBlock()
        }
        
        for (z in 1..zeroLen) {
            if (tmpIdx >= 8) cryptBlock()
            require(tmpBuf[tmpIdx] == ivPrev[tmpIdx]) { "zero check failed" }
            tmpIdx++
        }
        return outBuf
    }

    private val MIX_KEY_1 = byteArrayOf(0x33, 0x38, 0x36, 0x5A, 0x4A, 0x59, 0x21, 0x40, 0x23, 0x2A, 0x24, 0x25, 0x5E, 0x26, 0x29, 0x28)
    private val MIX_KEY_2 = byteArrayOf(0x2A, 0x2A, 0x23, 0x21, 0x28, 0x23, 0x24, 0x25, 0x26, 0x5E, 0x61, 0x31, 0x63, 0x5A, 0x2C, 0x54)

    private fun decryptV2Key(keyBuf: ByteArray): ByteArray {
        if (keyBuf.size >= 18 && keyBuf.sliceArray(0 until 18).decodeToString() == "QQMusic EncV2,Key:") {
            var out = decryptTencentTea(keyBuf.sliceArray(18 until keyBuf.size), MIX_KEY_1)
            out = decryptTencentTea(out, MIX_KEY_2)
            val keyDecStr = out.decodeToString()
            return Base64Decoder.decode(keyDecStr)
        }
        return keyBuf
    }

    private fun simpleMakeKey(salt: Int, length: Int): ByteArray {
        val keyBuf = ByteArray(length)
        for (i in 0 until length) {
            val tmp = tan(salt + i * 0.1)
            keyBuf[i] = (abs(tmp) * 100.0).toInt().toByte()
        }
        return keyBuf
    }

    private fun qmcDeriveKey(rawDecInput: ByteArray): ByteArray {
        if (rawDecInput.size < 16) return rawDecInput
        try {
            val rawDec = decryptV2Key(rawDecInput)
            val simpleKey = simpleMakeKey(106, 8)
            val teaKey = ByteArray(16)
            for (i in 0 until 8) {
                teaKey[i shl 1] = simpleKey[i]
                teaKey[(i shl 1) + 1] = rawDec[i]
            }
            val sub = decryptTencentTea(rawDec.sliceArray(8 until rawDec.size), teaKey)
            val finalKey = ByteArray(8 + sub.size)
            System.arraycopy(rawDec, 0, finalKey, 0, 8)
            System.arraycopy(sub, 0, finalKey, 8, sub.size)
            return finalKey
        } catch (e: Throwable) {
            return rawDecInput
        }
    }

    private class QmcRC4Cipher(val key: ByteArray) {
        val n = key.size
        val s = ByteArray(n)
        var hash: Long = 1
        init {
            for (i in 0 until n) s[i] = i.toByte()
            var j = 0
            for (i in 0 until n) {
                j = ((s[i].toInt() and 0xff) + j + (key[i % n].toInt() and 0xff)) % n
                val tmp = s[i]
                s[i] = s[j]
                s[j] = tmp
            }
            for (i in 0 until n) {
                val value = key[i].toInt() and 0xff
                if (value == 0) continue
                val nextHash = (hash * value) and 0xffffffffL
                if (nextHash == 0L || nextHash <= hash) break
                hash = nextHash
            }
        }
        fun getSegmentKey(id: Int): Int {
            val seed = key[id % n].toInt() and 0xff
            val idx = ((hash.toDouble() / ((id + 1) * seed)) * 100.0).toLong()
            return (idx % n).toInt()
        }
        fun decrypt(buf: ByteArray, offset: Int) {
            val segmentSize = 5120
            var toProcess = buf.size
            var processed = 0
            var curOff = offset
            
            fun postProcess(len: Int): Boolean {
                toProcess -= len
                processed += len
                curOff += len
                return toProcess == 0
            }
            
            if (curOff < 128) {
                val len = minOf(buf.size, 128 - curOff)
                for (i in 0 until len) {
                    buf[processed + i] = (buf[processed + i].toInt() xor (key[getSegmentKey(curOff + i)].toInt() and 0xff)).toByte()
                }
                if (postProcess(len)) return
            }
            
            fun encSegment(subBuf: ByteArray, off: Int, len: Int) {
                val curS = s.clone()
                val skipLen = (off % segmentSize) + getSegmentKey(off / segmentSize)
                var j = 0
                var k = 0
                for (i in -skipLen until len) {
                    j = (j + 1) % n
                    k = ((curS[j].toInt() and 0xff) + k) % n
                    val tmp = curS[j]
                    curS[j] = curS[k]
                    curS[k] = tmp
                    if (i >= 0) {
                        subBuf[i] = (subBuf[i].toInt() xor (curS[((curS[j].toInt() and 0xff) + (curS[k].toInt() and 0xff)) % n].toInt() and 0xff)).toByte()
                    }
                }
            }
            
            if (curOff % segmentSize != 0) {
                val len = minOf(segmentSize - (curOff % segmentSize), toProcess)
                val sub = buf.sliceArray(processed until processed + len)
                encSegment(sub, curOff, len)
                System.arraycopy(sub, 0, buf, processed, len)
                if (postProcess(len)) return
            }
            
            while (toProcess > segmentSize) {
                val sub = buf.sliceArray(processed until processed + segmentSize)
                encSegment(sub, curOff, segmentSize)
                System.arraycopy(sub, 0, buf, processed, segmentSize)
                postProcess(segmentSize)
            }
            
            if (toProcess > 0) {
                val sub = buf.sliceArray(processed until processed + toProcess)
                encSegment(sub, curOff, toProcess)
                System.arraycopy(sub, 0, buf, processed, toProcess)
            }
        }
    }

    private fun shiftMix(byte: Int, shift: Int): Int {
        val s = shift and 7
        if (s == 0) return byte and 0xff
        val b = byte and 0xff
        return ((b shl s) or (b ushr s)) and 0xff
    }

    private fun keyCompress(ekey: ByteArray): ByteArray {
        val n = ekey.size
        if (n == 0) throw IllegalArgumentException("ekey is empty")
        val out = ByteArray(128)
        for (i in 0 until 128) {
            val idx = (i * i + 71214) % n
            val shift = (idx + 4) % 8
            out[i] = shiftMix(ekey[idx].toInt(), shift).toByte()
        }
        return out
    }

    private fun getMapMask(derivedKey: ByteArray): ByteArray {
        val wkey = keyCompress(derivedKey)
        val mask = ByteArray(32768)
        for (i in 0 until 32768) {
            mask[i] = wkey[i % 128]
        }
        return mask
    }

    private fun applyMask(buf: ByteArray, mask: ByteArray) {
        val len = buf.size
        val limit1 = minOf(len, 32768)
        for (i in 0 until limit1) {
            buf[i] = (buf[i].toInt() xor mask[i].toInt()).toByte()
        }
        for (i in 32768 until len) {
            buf[i] = (buf[i].toInt() xor mask[i % 32767].toInt()).toByte()
        }
    }

    fun decryptV1(input: ByteArray): ByteArray {
        val out = input.clone()
        applyMask(out, V1_MASK)
        return out
    }

    fun decryptV2(input: ByteArray, ekey: ByteArray): ByteArray {
        val derivedKey = qmcDeriveKey(ekey)
        val out = input.clone()
        if (derivedKey.size > 300) {
            val rc4 = QmcRC4Cipher(derivedKey)
            rc4.decrypt(out, 0)
        } else {
            val mask = getMapMask(derivedKey)
            applyMask(out, mask)
        }
        return out
    }

    override fun decrypt(input: ByteArray): DecryptResult {
        val detected = detectKey(input)
        val audio = if (detected != null) {
            val cipher = input.sliceArray(0 until detected.audioLen)
            decryptV2(cipher, detected.ekey)
        } else {
            decryptV1(input)
        }
        return DecryptResult(audio = audio, format = FormatSniffer.sniff(audio))
    }
}
