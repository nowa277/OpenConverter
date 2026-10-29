package com.openconverter.app.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconverter.app.decoders.SyntheticNcm
import com.openconverter.app.ffmpeg.FfmpegKitRunner
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSessionCancellationTest {
    @Test fun active_native_cancel_is_session_scoped_and_allows_next_conversion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "native-cancel-test").apply { mkdirs() }
        val input = File(root, "long.wav")
        val pcm = SyntheticNcm.audio.copyOfRange(44, SyntheticNcm.audio.size)
        input.outputStream().use { output ->
            val header = SyntheticNcm.audio.copyOf(44)
            java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                putInt(4, 36 + pcm.size * 3600); putInt(40, pcm.size * 3600)
            }
            output.write(header)
            repeat(3600) { output.write(pcm) }
        }
        val short = File(root, "short.wav").apply { writeBytes(SyntheticNcm.audio) }
        val runner = FfmpegKitRunner()
        val started = CompletableDeferred<Unit>()
        try {
            val first = async(Dispatchers.IO) {
                runner.execute(input.path, File(root, "first.mp3").path, "mp3", "128k", 3_600_000, { if (it > 0) started.complete(Unit) })
            }
            withTimeout(15_000) { started.await() }
            val second = async(Dispatchers.IO) { runner.execute(short.path, File(root, "second.mp3").path, "mp3", "128k") }
            withTimeout(10_000) { first.cancelAndJoin() }
            assertTrue(withTimeout(15_000) { second.await() }.isSuccess)
            assertTrue(runner.execute(short.path, File(root, "third.mp3").path, "mp3", "128k").isSuccess)
            for (name in listOf("second.mp3", "third.mp3")) {
                val media = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(File(root, name).path).mediaInformation
                assertTrue(media.duration.toDouble() in 0.9..1.3)
            }
        } finally { root.deleteRecursively() }
    }
}
