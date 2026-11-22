package com.degoos.metrics

import java.util.function.Supplier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MetricsBridgeTest {
    private val owner = "bridge-test"

    @AfterTest
    fun cleanUp() = MetricsBridge.unregister(owner)

    private fun lines(name: String) = SharedMetrics.registry.collect().single { it.name == name }.lines

    @Test
    fun `gauge appears with labels and values`() {
        MetricsBridge.gauge(owner, "bridge_gauge", "g", listOf("k"), Supplier { mapOf(listOf("a") to 2.0, listOf("b") to 3.5) })
        val byLabel = lines("bridge_gauge").associate { it.labels.single().second to it.value }
        assertEquals(mapOf("a" to 2.0, "b" to 3.5), byLabel)
    }

    @Test
    fun `counter and summary consumers update values`() {
        val counter = MetricsBridge.counter(owner, "bridge_total", "c", listOf("kind"))
        counter.accept(listOf("x"), 1.0)
        counter.accept(listOf("x"), 2.5)
        assertEquals(3.5, lines("bridge_total").single().value)

        val summary = MetricsBridge.summary(owner, "bridge_seconds", "s", listOf("kit"))
        summary.accept(listOf("a"), 1.5)
        summary.accept(listOf("a"), 2.5)
        val byName = lines("bridge_seconds").associate { it.name to it.value }
        assertEquals(mapOf("bridge_seconds_count" to 2.0, "bridge_seconds_sum" to 4.0), byName)
    }

    @Test
    fun `bad updates never throw`() {
        val counter = MetricsBridge.counter(owner, "bridge_bad_total", "c", listOf("kind"))
        counter.accept(emptyList(), 1.0)
        counter.accept(listOf("x"), -1.0)
        counter.accept(listOf("x"), Double.NaN)
        val summary = MetricsBridge.summary(owner, "bridge_bad_seconds", "s", listOf("kit"))
        summary.accept(listOf("a", "b"), 1.0)
        summary.accept(listOf("a"), Double.POSITIVE_INFINITY)
        assertTrue(lines("bridge_bad_total").isEmpty())
    }

    @Test
    fun `invalid metric name throws at registration`() {
        assertFailsWith<IllegalArgumentException> { MetricsBridge.counter(owner, "1bad", "c", emptyList()) }
        assertFailsWith<IllegalArgumentException> { MetricsBridge.gauge(owner, "bad-name", "g", emptyList(), Supplier { emptyMap() }) }
        MetricsBridge.counter(owner, "bridge_dup_total", "c", emptyList())
        assertFailsWith<IllegalArgumentException> { MetricsBridge.counter(owner, "bridge_dup_total", "c", emptyList()) }
    }

    @Test
    fun `unregister removes metrics and world grouper`() {
        MetricsBridge.counter(owner, "bridge_gone_total", "c", emptyList())
        MetricsBridge.groupWorlds(owner) { "grouped" }
        assertEquals("grouped", SharedMetrics.worldGroups.label("w"))
        MetricsBridge.unregister(owner)
        assertTrue(SharedMetrics.registry.collect().none { it.name == "bridge_gone_total" })
        assertEquals("w", SharedMetrics.worldGroups.label("w"))
        MetricsBridge.unregister(owner)
    }
}
