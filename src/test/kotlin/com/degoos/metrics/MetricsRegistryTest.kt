package com.degoos.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetricsRegistryTest {
    private val log = RecordingLog()
    private val registry = MetricsRegistry(log)

    private fun lines(name: String) = registry.collect().single { it.name == name }.lines

    @Test
    fun `gauge callback is evaluated at scrape time`() {
        var value = 1.0
        registry.gauge("test_gauge", "g", listOf("k")) { listOf(Sample(value, "a")) }
        assertEquals(1.0, lines("test_gauge").single().value)
        value = 7.0
        assertEquals(7.0, lines("test_gauge").single().value)
    }

    @Test
    fun `rejects invalid and duplicate names`() {
        assertFailsWith<IllegalArgumentException> { registry.counter("1bad", "h") }
        assertFailsWith<IllegalArgumentException> { registry.counter("bad-name", "h") }
        assertFailsWith<IllegalArgumentException> { registry.gauge("ok", "h", listOf("bad-label")) { emptyList() } }
        registry.counter("dup", "h")
        assertFailsWith<IllegalArgumentException> { registry.summary("dup", "h") }
        registry.counter("a:b_c9", "h")
    }

    @Test
    fun `counter adds up per label set and refuses bad input`() {
        val counter = registry.counter("test_total", "h", listOf("kind"))
        counter.inc("x")
        counter.add(2.5, "x")
        counter.inc("y")
        val byKind = lines("test_total").associate { it.labels.single().second to it.value }
        assertEquals(mapOf("x" to 3.5, "y" to 1.0), byKind)
        assertFailsWith<IllegalArgumentException> { counter.inc() }
        assertFailsWith<IllegalArgumentException> { counter.add(-1.0, "x") }
    }

    @Test
    fun `invalid label values are dropped and logged once`() {
        val counter = registry.counter("test_total", "h", listOf("kind"))
        counter.inc("has space")
        counter.inc("bad/value")
        counter.inc("")
        assertTrue(lines("test_total").isEmpty())
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun `summary exposes count and sum`() {
        val summary = registry.summary("test_seconds", "h", listOf("kit"))
        summary.observe(1.5, "a")
        summary.observe(2.5, "a")
        val byName = lines("test_seconds").associate { it.name to it.value }
        assertEquals(mapOf("test_seconds_count" to 2.0, "test_seconds_sum" to 4.0), byName)
    }

    @Test
    fun `series cap drops extra series and logs once per metric`() {
        val counter = registry.counter("capped_total", "h", listOf("id"))
        repeat(LabelRules.MAX_SERIES_PER_METRIC + 25) { counter.inc("id$it") }
        counter.inc("id0")
        val lines = lines("capped_total")
        assertEquals(LabelRules.MAX_SERIES_PER_METRIC, lines.size)
        assertEquals(2.0, lines.single { it.labels.single().second == "id0" }.value)
        assertEquals(1, log.warnings.count { it.contains("capped_total") })
    }

    @Test
    fun `gauge output is capped and validated`() {
        registry.gauge("big", "h", listOf("id")) { (0 until 600).map { Sample(1.0, "id$it") } + Sample(1.0, "bad value") }
        assertEquals(LabelRules.MAX_SERIES_PER_METRIC, lines("big").size)
        registry.collect()
        assertEquals(2, log.warnings.size)
    }

    @Test
    fun `unregister removes only that owner and frees the name`() {
        registry.counter("mine_total", "h", owner = "mine")
        registry.counter("other_total", "h", owner = "other")
        registry.unregister("mine")
        assertEquals(listOf("other_total"), registry.collect().map { it.name })
        registry.counter("mine_total", "h", owner = "mine")
    }

    @Test
    fun `a failing gauge is skipped and logged once`() {
        registry.gauge("broken", "h", emptyList()) { error("boom") }
        registry.gauge("fine", "h", emptyList()) { listOf(Sample(1.0)) }
        repeat(3) { assertEquals(listOf("fine"), registry.collect().map { it.name }) }
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun `sanitizes label values`() {
        assertEquals("my_world_", LabelRules.sanitizeLabelValue("my world!"))
        assertEquals(64, LabelRules.sanitizeLabelValue("x".repeat(100)).length)
        assertEquals("_", LabelRules.sanitizeLabelValue(""))
        assertTrue(LabelRules.isValidLabelValue(LabelRules.sanitizeLabelValue("ünï/cøde")))
    }
}
