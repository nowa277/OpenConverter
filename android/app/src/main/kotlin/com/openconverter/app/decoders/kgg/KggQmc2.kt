package com.openconverter.app.decoders.kgg

fun interface KggStreamCipher {
    fun apply(buffer: ByteArray, length: Int, absoluteOffset: Long)
}

object KggQmc2 {
    fun cipher(key: ByteArray): KggStreamCipher {
        require(key.isNotEmpty()) { "QMC2 key is empty" }
        return if (key.size <= 300) MapCipher(key.copyOf()) else Rc4Cipher(key.copyOf())
    }

    private class MapCipher(private val key: ByteArray) : KggStreamCipher {
        override fun apply(buffer: ByteArray, length: Int, absoluteOffset: Long) {
            validate(buffer, length, absoluteOffset)
            repeat(length) { index ->
                var offset = absoluteOffset + index
                if (offset > MAP_OFFSET_BOUNDARY) offset %= MAP_OFFSET_BOUNDARY
                val keyIndex = ((offset * offset + MAP_INDEX_OFFSET) % key.size).toInt()
                val value = key[keyIndex].toInt() and 0xff
                val shift = ((keyIndex and 7) + 4) % 8
                val mask = ((value shl shift) or (value ushr shift)) and 0xff
                buffer[index] = (buffer[index].toInt() xor mask).toByte()
            }
        }
    }

    private class Rc4Cipher(private val key: ByteArray) : KggStreamCipher {
        private val box = ByteArray(key.size) { it.toByte() }
        private val hash: Long

        init {
            var swapIndex = 0
            box.indices.forEach { index ->
                swapIndex = (swapIndex + (box[index].toInt() and 0xff) + (key[index].toInt() and 0xff)) % key.size
                val value = box[index]
                box[index] = box[swapIndex]
                box[swapIndex] = value
            }

            var value = 1L
            for (byte in key) {
                val unsigned = byte.toLong() and 0xff
                if (unsigned == 0L) continue
                val next = (value * unsigned) and 0xffffffffL
                if (next == 0L || next <= value) break
                value = next
            }
            hash = value
        }

        override fun apply(buffer: ByteArray, length: Int, absoluteOffset: Long) {
            validate(buffer, length, absoluteOffset)
            var offset = absoluteOffset
            var processed = 0
            var remaining = length

            if (offset < RC4_FIRST_SEGMENT_SIZE) {
                val count = minOf(remaining.toLong(), RC4_FIRST_SEGMENT_SIZE - offset).toInt()
                repeat(count) { index ->
                    val keyIndex = segmentSkip(offset + index)
                    buffer[processed + index] =
                        (buffer[processed + index].toInt() xor key[keyIndex].toInt()).toByte()
                }
                offset += count
                processed += count
                remaining -= count
            }

            if (remaining > 0 && offset % RC4_SEGMENT_SIZE != 0L) {
                val count = minOf(remaining.toLong(), RC4_SEGMENT_SIZE - offset % RC4_SEGMENT_SIZE).toInt()
                applySegment(buffer, processed, count, offset)
                offset += count
                processed += count
                remaining -= count
            }

            while (remaining > RC4_SEGMENT_SIZE) {
                applySegment(buffer, processed, RC4_SEGMENT_SIZE.toInt(), offset)
                offset += RC4_SEGMENT_SIZE
                processed += RC4_SEGMENT_SIZE.toInt()
                remaining -= RC4_SEGMENT_SIZE.toInt()
            }

            if (remaining > 0) applySegment(buffer, processed, remaining, offset)
        }

        private fun applySegment(buffer: ByteArray, start: Int, length: Int, offset: Long) {
            val state = box.copyOf()
            var j = 0
            var k = 0
            val skip = (offset % RC4_SEGMENT_SIZE).toInt() + segmentSkip(offset / RC4_SEGMENT_SIZE)
            repeat(skip + length) { step ->
                j = (j + 1) % state.size
                k = ((state[j].toInt() and 0xff) + k) % state.size
                val value = state[j]
                state[j] = state[k]
                state[k] = value
                if (step >= skip) {
                    val stream = state[((state[j].toInt() and 0xff) + (state[k].toInt() and 0xff)) % state.size]
                    val index = start + step - skip
                    buffer[index] = (buffer[index].toInt() xor stream.toInt()).toByte()
                }
            }
        }

        private fun segmentSkip(segmentId: Long): Int {
            val seed = key[(segmentId % key.size).toInt()].toLong() and 0xff
            if (seed == 0L) return 0
            val index = (hash.toDouble() / ((segmentId + 1).toDouble() * seed.toDouble()) * 100.0).toLong()
            return (index % key.size).toInt()
        }
    }

    private fun validate(buffer: ByteArray, length: Int, absoluteOffset: Long) {
        require(length in 0..buffer.size) { "QMC2 buffer length is invalid" }
        require(absoluteOffset >= 0) { "QMC2 offset is negative" }
    }

    private const val MAP_OFFSET_BOUNDARY = 0x7fffL
    private const val MAP_INDEX_OFFSET = 71214L
    private const val RC4_FIRST_SEGMENT_SIZE = 128L
    private const val RC4_SEGMENT_SIZE = 5120L
}
