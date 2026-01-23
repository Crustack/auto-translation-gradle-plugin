package io.github.philkes.auto.translation.plugin

import io.github.philkes.auto.translation.plugin.util.StringsXmlHelper
import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StringsXmlHelperTest {

    @Test
    fun `escapeForAndroid escapes apostrophes`() {
        val helper = createHelper()
        assertEquals("aujourd\\'hui", helper.escapeForAndroid("aujourd'hui"))
        assertEquals("it\\'s working", helper.escapeForAndroid("it's working"))
        assertEquals("no apostrophe", helper.escapeForAndroid("no apostrophe"))
    }

    @Test
    fun `escapeForAndroid escapes double quotes`() {
        val helper = createHelper()
        assertEquals("say \\\"hello\\\"", helper.escapeForAndroid("say \"hello\""))
    }

    @Test
    fun `escapeForAndroid escapes backslashes`() {
        val helper = createHelper()
        assertEquals("path\\\\to\\\\file", helper.escapeForAndroid("path\\to\\file"))
    }

    @Test
    fun `escapeForAndroid escapes at sign at start`() {
        val helper = createHelper()
        assertEquals("\\@mention", helper.escapeForAndroid("@mention"))
        assertEquals("email@example.com", helper.escapeForAndroid("email@example.com"))
    }

    @Test
    fun `escapeForAndroid escapes question mark at start`() {
        val helper = createHelper()
        assertEquals("\\?attr", helper.escapeForAndroid("?attr"))
        assertEquals("what?", helper.escapeForAndroid("what?"))
    }

    @Test
    fun `escapeForAndroid handles empty string`() {
        val helper = createHelper()
        assertEquals("", helper.escapeForAndroid(""))
    }

    @Test
    fun `escapeForAndroid handles combined special characters`() {
        val helper = createHelper()
        assertEquals(
            "It\\'s a \\\"test\\\" with \\\\ and more",
            helper.escapeForAndroid("It's a \"test\" with \\ and more"),
        )
    }

    // --- Normalisation tests: already-escaped input should not be double-escaped ---

    @Test
    fun `escapeForAndroid normalises already escaped apostrophes`() {
        val helper = createHelper()
        assertEquals("aujourd\\'hui", helper.escapeForAndroid("aujourd\\'hui"))
        assertEquals("aujourd\\'hui", helper.escapeForAndroid("aujourd'hui"))
    }

    @Test
    fun `escapeForAndroid normalises already escaped quotes`() {
        val helper = createHelper()
        assertEquals("say \\\"hello\\\"", helper.escapeForAndroid("say \\\"hello\\\""))
        assertEquals("say \\\"hello\\\"", helper.escapeForAndroid("say \"hello\""))
    }

    @Test
    fun `escapeForAndroid normalises already escaped backslashes`() {
        val helper = createHelper()
        assertEquals("path\\\\file", helper.escapeForAndroid("path\\\\file"))
        assertEquals("path\\\\file", helper.escapeForAndroid("path\\file"))
    }

    @Test
    fun `escapeForAndroid normalises already escaped at sign`() {
        val helper = createHelper()
        assertEquals("\\@mention", helper.escapeForAndroid("\\@mention"))
        assertEquals("\\@mention", helper.escapeForAndroid("@mention"))
    }

    @Test
    fun `escapeForAndroid normalises already escaped question mark`() {
        val helper = createHelper()
        assertEquals("\\?attr", helper.escapeForAndroid("\\?attr"))
        assertEquals("\\?attr", helper.escapeForAndroid("?attr"))
    }

    // --- Idempotency tests: escaping twice should give the same result ---

    @Test
    fun `escapeForAndroid is idempotent for all special characters`() {
        val helper = createHelper()
        val inputs =
            listOf(
                "aujourd'hui",
                "say \"hello\"",
                "path\\to\\file",
                "@mention",
                "?attribute",
                "It's a \"complex\" test with \\ and @start and ?question",
            )
        for (input in inputs) {
            val once = helper.escapeForAndroid(input)
            val twice = helper.escapeForAndroid(once)
            assertEquals(once, twice, "Escaping should be idempotent for: $input")
        }
    }

    // --- Mixed input tests: partially escaped input ---

    @Test
    fun `escapeForAndroid handles mixed escaped and unescaped apostrophes`() {
        val helper = createHelper()
        // One escaped, one not
        assertEquals("it\\'s today\\'s test", helper.escapeForAndroid("it\\'s today's test"))
        assertEquals("it\\'s today\\'s test", helper.escapeForAndroid("it's today\\'s test"))
    }

    @Test
    fun `escapeForAndroid handles mixed escaped and unescaped quotes`() {
        val helper = createHelper()
        assertEquals(
            "say \\\"hello\\\" and \\\"goodbye\\\"",
            helper.escapeForAndroid("say \\\"hello\\\" and \"goodbye\""),
        )
    }

    @Test
    fun `escapeForAndroid handles all escapables mixed in one string`() {
        val helper = createHelper()
        // Input with all special characters, some escaped some not
        val input = "It's a \"test\" with \\ and \\' and \\\" mixed"
        val expected = "It\\'s a \\\"test\\\" with \\\\ and \\' and \\\" mixed"
        assertEquals(expected, helper.escapeForAndroid(input))
    }

    // --- Edge cases ---

    @Test
    fun `escapeForAndroid handles consecutive apostrophes`() {
        val helper = createHelper()
        assertEquals("\\'\\'\\'", helper.escapeForAndroid("'''"))
    }

    @Test
    fun `escapeForAndroid handles consecutive quotes`() {
        val helper = createHelper()
        assertEquals("\\\"\\\"\\\"", helper.escapeForAndroid("\"\"\""))
    }

    @Test
    fun `escapeForAndroid handles consecutive backslashes`() {
        val helper = createHelper()
        // Input: 3 backslashes -> normalise (2->1) -> 2 backslashes -> escape -> 4 backslashes
        assertEquals("\\\\\\\\", helper.escapeForAndroid("\\\\\\"))
        // Input: 2 backslashes -> normalise (2->1) -> 1 backslash -> escape -> 2 backslashes
        assertEquals("\\\\", helper.escapeForAndroid("\\\\"))
        // Input: 1 backslash -> no normalise change -> escape -> 2 backslashes
        assertEquals("\\\\", helper.escapeForAndroid("\\"))
    }

    @Test
    fun `escapeForAndroid handles backslash followed by apostrophe unescaped`() {
        val helper = createHelper()
        // A literal backslash followed by apostrophe (not an escape sequence)
        // Input: actual backslash + apostrophe = \'
        // After normalisation: ' (unescape \')
        // After escaping: \'
        assertEquals("\\'", helper.escapeForAndroid("\\'"))
    }

    @Test
    fun `escapeForAndroid escapes backslash in escape sequences`() {
        val helper = createHelper()
        // Backslash before n/t gets escaped (the n/t are preserved)
        assertEquals("line1\\\\nline2", helper.escapeForAndroid("line1\\nline2"))
        assertEquals("col1\\\\tcol2", helper.escapeForAndroid("col1\\tcol2"))
        // Actual newline/tab characters pass through unchanged
        assertEquals("line1\nline2", helper.escapeForAndroid("line1\nline2"))
        assertEquals("col1\tcol2", helper.escapeForAndroid("col1\tcol2"))
    }

    @Test
    fun `escapeForAndroid handles unicode characters`() {
        val helper = createHelper()
        // Unicode should pass through unchanged (except escapable chars)
        assertEquals("Ім\\'я", helper.escapeForAndroid("Ім'я"))
        assertEquals("日本語", helper.escapeForAndroid("日本語"))
        assertEquals("émoji 🎉", helper.escapeForAndroid("émoji 🎉"))
    }

    // --- Write tests with already-escaped input ---

    @Test
    fun `write handles already escaped input without double escaping`(@TempDir tempDir: File) {
        val helper = createHelper()
        val outputFile = File(tempDir, "strings.xml")
        val entries =
            mapOf(
                "pre_escaped" to "aujourd\\'hui",
                "unescaped" to "aujourd'hui",
            )

        helper.write(outputFile, entries)

        val content = outputFile.readText()
        // Both should result in the same escaped output (aujourd\'hui)
        assertTrue(
            content.contains("aujourd\\'hui"),
            "Pre-escaped input should not be double-escaped",
        )
        // Should NOT contain double-escaped version
        assertTrue(
            !content.contains("aujourd\\\\'hui") && !content.contains("aujourd\\\\\\'hui"),
            "Should not have double-escaped apostrophe",
        )
        // Verify both entries produce the same escaped string by checking for two occurrences
        val matches = Regex("aujourd\\\\'hui").findAll(content).count()
        assertEquals(2, matches, "Both entries should have identical escaped output")
    }

    @Test
    fun `write escapes apostrophes in output file`(@TempDir tempDir: File) {
        val helper = createHelper()
        val outputFile = File(tempDir, "strings.xml")
        val entries =
            mapOf(
                "greeting" to "Aujourd'hui",
                "farewell" to "It's time to say goodbye",
            )

        helper.write(outputFile, entries)

        val content = outputFile.readText()
        assertTrue(content.contains("Aujourd\\'hui"), "Apostrophe should be escaped in output")
        assertTrue(content.contains("It\\'s time"), "Apostrophe should be escaped in output")
    }

    @Test
    fun `write escapes quotes in output file`(@TempDir tempDir: File) {
        val helper = createHelper()
        val outputFile = File(tempDir, "strings.xml")
        val entries = mapOf("quoted" to "Say \"hello\"")

        helper.write(outputFile, entries)

        val content = outputFile.readText()
        assertTrue(content.contains("Say \\\"hello\\\""), "Quotes should be escaped in output")
    }

    @Test
    fun `write escapes special start characters in output file`(@TempDir tempDir: File) {
        val helper = createHelper()
        val outputFile = File(tempDir, "strings.xml")
        val entries =
            mapOf(
                "at_start" to "@mention",
                "question_start" to "?attribute",
            )

        helper.write(outputFile, entries)

        val content = outputFile.readText()
        assertTrue(content.contains("\\@mention"), "@ at start should be escaped")
        assertTrue(content.contains("\\?attribute"), "? at start should be escaped")
    }

    @Test
    fun `write escapes plurals content`(@TempDir tempDir: File) {
        val helper = createHelper()
        val outputFile = File(tempDir, "strings.xml")
        val entries =
            mapOf(
                "items[one]" to "it's one item",
                "items[other]" to "they're multiple items",
            )

        helper.write(outputFile, entries)

        val content = outputFile.readText()
        assertTrue(content.contains("it\\'s one item"), "Apostrophe in plural should be escaped")
        assertTrue(
            content.contains("they\\'re multiple items"),
            "Apostrophe in plural should be escaped",
        )
    }

    private fun createHelper(): StringsXmlHelper {
        val project = ProjectBuilder.builder().build()
        return StringsXmlHelper(project.logger)
    }
}
