package com.openconverter.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBytesTest {
    @Test fun negative_returns_empty() = assertEquals("", formatBytes(-1L))
    @Test fun zero() = assertEquals("0 B", formatBytes(0L))
    @Test fun under_1k() = assertEquals("512 B", formatBytes(512L))
    @Test fun just_under_1k() = assertEquals("1023 B", formatBytes(1023L))
    @Test fun exactly_1k() = assertEquals("1.0 KB", formatBytes(1024L))
    @Test fun one_and_half_k() = assertEquals("1.5 KB", formatBytes(1536L))
    @Test fun one_mb() = assertEquals("1.0 MB", formatBytes(1_048_576L))
    @Test fun four_point_two_mb() = assertEquals("4.2 MB", formatBytes(4_482_658L))
    @Test fun one_gb() = assertEquals("1.0 GB", formatBytes(1_073_741_824L))
}
