package com.lexipopup

import org.junit.Assert.*
import org.junit.Test

class LookupWordNormalizationTest {

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z'-]"), "")
            .trimStart('\'', '-')
            .trimEnd('\'', '-')

    @Test
    fun `normalizes basic word`() = assertEquals("ephemeral", normalize("Ephemeral"))

    @Test
    fun `strips punctuation`() = assertEquals("hello", normalize("hello!"))

    @Test
    fun `handles leading trailing spaces`() = assertEquals("test", normalize("  test  "))

    @Test
    fun `handles apostrophe in word`() = assertEquals("it's", normalize("It's"))

    @Test
    fun `empty input returns empty`() = assertEquals("", normalize(""))

    @Test
    fun `strips numbers`() = assertEquals("hello", normalize("hello123"))

    @Test
    fun `lowercase conversion`() = assertEquals("procrastination", normalize("PROCRASTINATION"))
}
