package com.degoos.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegistryHardeningTest {
    private val log = RecordingLog()
    private val registry = MetricsRegistry(log)

    @Test
    fun `a gauge throwing NoClassDefFoundError is skipped and logged once`() {
        registry.gauge("optional_dep", "h", emptyList()) { throw NoClassDefFoundError("com/other/Missing") }
        registry.gauge("fine", "h", emptyList()) { listOf(Sample(1.0)) }
        repeat(3) { assertEquals(listOf("fine"), registry.collect().map { it.name }) }
        assertEquals(1, log.warnings.count { it.contains("optional_dep") })
    }

    @Test
    fun `a virtual machine error is not swallowed`() {
        registry.gauge("oom", "h", emptyList()) { throw OutOfMemoryError("test") }
        assertFailsWith<OutOfMemoryError> { registry.collect() }
    }

    @Test
    fun `duplicate label sets are dropped keeping the first and logged once`() {
        registry.gauge("dups", "h", listOf("k")) { listOf(Sample(1.0, "a"), Sample(2.0, "a"), Sample(3.0, "b")) }
        repeat(2) {
            val lines = registry.collect().single().lines
            assertEquals(listOf(1.0, 3.0), lines.map { it.value })
        }
        assertEquals(1, log.warnings.count { it.contains("duplicate") })
    }

    @Test
    fun `a name colliding with a summary sum or count is refused`() {
        registry.summary("job_seconds", "h")
        assertFailsWith<IllegalArgumentException> { registry.counter("job_seconds_sum", "h") }
        assertFailsWith<IllegalArgumentException> { registry.gauge("job_seconds_count", "h", emptyList()) { emptyList() } }
        assertFailsWith<IllegalArgumentException> { registry.counter("job_seconds", "h") }
    }

    @Test
    fun `a summary whose sum or count collides with an existing name is refused`() {
        registry.counter("lat_sum", "h")
        registry.counter("other_count", "h")
        assertFailsWith<IllegalArgumentException> { registry.summary("lat", "h") }
        assertFailsWith<IllegalArgumentException> { registry.summary("other", "h") }
        assertTrue(registry.collect().map { it.name }.containsAll(listOf("lat_sum", "other_count")))
    }

    @Test
    fun `unregister is always safe and handles stay valid after re-registering`() {
        registry.unregister("nobody")
        val first = registry.counter("x_total", "h", owner = "dep")
        registry.unregister("dep")
        registry.unregister("dep")
        first.inc()
        val second = registry.counter("x_total", "h", owner = "dep")
        second.inc()
        assertEquals(1.0, registry.collect().single().lines.single().value)
    }
}
