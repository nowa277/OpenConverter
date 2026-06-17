package com.openconverter.app.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconverter.app.ffmpeg.FfmpegBridge
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Instrumented end-to-end test for the conversion pipeline.
 *
 * Runs on a real Android device or emulator. Pushes a real NCM file
 * (via scripts/test-android.sh) to /data/local/tmp/test-input.ncm,
 * exercises the full orchestrator (NcmDecoder → ffmpeg probe → output),
 * and writes the result to the app's cacheDir for visual inspection.
 *
 * Skips via assumeTrue if the test input wasn't pushed — the test is
 * only meaningful when the script has set up the input file.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndConversionTest {

    @Test
    fun ncm_decrypts_to_mp3_on_real_device() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ncmFile = File(context.cacheDir, "test-input.ncm")

        // Skip if the test input wasn't pushed via adb (manual setup).
        // The script that pushes it: scripts/test-android.sh
        val pushedNcm = File("/data/local/tmp/test-input.ncm")
        assumeTrue(
            "test input not pushed; run scripts/test-android.sh first",
            pushedNcm.exists()
        )

        // Read the pushed NCM
        val input = pushedNcm.readBytes()

        // Run the orchestrator (no ffmpeg transcode — M3 passthrough)
        val orchestrator = ConversionOrchestrator(FfmpegBridge)
        val result = orchestrator.convertOneInMemory(
            input = input,
            ekey = null,
            targetFormat = "mp3",
            bitrateKbps = 256,
        )

        // Sanity checks
        assertTrue(result.encoded.isNotEmpty(), "Output should not be empty")
        assertTrue(
            result.encoded.size < input.size,
            "Output (${result.encoded.size}) should be smaller than input (${input.size})"
        )
        assertTrue(
            result.sourceFormat == "ncm",
            "Source format should be 'ncm', got '${result.sourceFormat}'"
        )

        // Write output for visual inspection
        ncmFile.writeBytes(result.encoded)
    }
}
