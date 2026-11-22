package com.degoos.metrics

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetricsConfigTest {
    private val token = "a".repeat(32)

    @Test
    fun `defaults are written when the file is missing and serve nothing`() {
        val dir = Files.createTempDirectory("metrics-config")
        val file = dir.resolve("nested").resolve(MetricsConfig.FILE_NAME)
        val config = MetricsConfig.loadOrCreate(file).getOrThrow()
        assertEquals(MetricsConfig("0.0.0.0", 0, ""), config)
        assertEquals(config, MetricsConfig.parse(file.readText()).getOrThrow())
        assertNotNull(config.problem())
    }

    @Test
    fun `validates port and token length`() {
        assertNull(MetricsConfig("0.0.0.0", 9100, token).problem())
        assertNotNull(MetricsConfig("0.0.0.0", 0, token).problem())
        assertNotNull(MetricsConfig("0.0.0.0", 65536, token).problem())
        assertNotNull(MetricsConfig("0.0.0.0", 9100, "a".repeat(31)).problem())
        assertNull(MetricsConfig("0.0.0.0", 65535, "a".repeat(32)).problem())
    }

    @Test
    fun `parses an existing file`() {
        val config = MetricsConfig.parse("{ \"bind\": \"172.18.0.1\", \"port\": 9100, \"token\": \"$token\" }").getOrThrow()
        assertEquals(MetricsConfig("172.18.0.1", 9100, token), config)
    }

    @Test
    fun `rejects broken files with a message that does not contain the token`() {
        val bad = listOf(
            "not json",
            "{\"port\": \"9100\"}",
            "{\"port\": 1.5}",
            "{\"token\": \"$token\" ",
            "{\"token\": {\"a\": 1}}",
        )
        for (text in bad) {
            val failure = MetricsConfig.parse(text).exceptionOrNull()
            assertNotNull(failure, text)
            assertTrue(!failure.message.orEmpty().contains(token))
        }
    }

    @Test
    fun `an unreadable file is a failure not a crash`() {
        val dir = Files.createTempDirectory("metrics-config")
        assertNotNull(MetricsConfig.loadOrCreate(dir).exceptionOrNull())
    }

    @Test
    fun `toString hides the token and json round trips special characters`() {
        val config = MetricsConfig("0.0.0.0", 1, "t\"o\\k\nen")
        assertTrue(!config.toString().contains("t\"o"))
        assertEquals(config, MetricsConfig.parse(config.toJson()).getOrThrow())
    }
}
