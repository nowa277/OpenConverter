package com.openconverter.app.decoders.kgg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KggKeyMapTest {
    @Test
    fun parse_accepts_line_endings_blank_lines_and_last_duplicate() {
        val parsed = KggKeyMap.parse("first\$one\r\n\r\nsecond\$two\nfirst\$new\n")

        assertEquals(mapOf("first" to "new", "second" to "two"), parsed)
    }

    @Test
    fun parse_rejects_malformed_nonblank_lines() {
        listOf(
            "missing-separator",
            "id\$value\$extra",
            "\$value",
            "id\$",
            "   \$value",
            "id\$   ",
        ).forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) {
                KggKeyMap.parse(text)
            }
        }
    }

    @Test
    fun serialize_is_sorted_and_has_terminal_newline() {
        assertEquals("a\$first\nz\$last\n", KggKeyMap.serialize(mapOf("z" to "last", "a" to "first")))
        assertEquals("", KggKeyMap.serialize(emptyMap()))
    }

    @Test
    fun merge_counts_additions_and_changed_values_without_mutating_inputs() {
        val current = linkedMapOf("same" to "one", "changed" to "old")
        val incoming = linkedMapOf("same" to "one", "changed" to "new", "added" to "value")

        val result = KggKeyMap.merge(current, incoming)

        assertEquals(mapOf("same" to "one", "changed" to "new", "added" to "value"), result.merged)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(mapOf("same" to "one", "changed" to "old"), current)
        assertEquals(mapOf("same" to "one", "changed" to "new", "added" to "value"), incoming)
    }
}
