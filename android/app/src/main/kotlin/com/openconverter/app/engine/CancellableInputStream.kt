package com.openconverter.app.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.io.FilterInputStream
import java.io.InputStream

/** Checks cancellation between bounded decoder reads without buffering the file. */
internal class CancellableInputStream(input: InputStream, private val job: Job) : FilterInputStream(input) {
    override fun read(): Int { job.ensureActive(); return `in`.read() }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        job.ensureActive()
        return `in`.read(bytes, offset, length)
    }
    override fun skip(count: Long): Long { job.ensureActive(); return `in`.skip(count) }
}
