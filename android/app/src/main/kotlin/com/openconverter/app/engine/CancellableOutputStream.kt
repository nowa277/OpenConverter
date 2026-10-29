package com.openconverter.app.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.io.FilterOutputStream
import java.io.OutputStream

internal class CancellableOutputStream(output: OutputStream, private val job: Job) : FilterOutputStream(output) {
    override fun write(value: Int) { job.ensureActive(); out.write(value) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        job.ensureActive()
        out.write(bytes, offset, length)
    }
}
