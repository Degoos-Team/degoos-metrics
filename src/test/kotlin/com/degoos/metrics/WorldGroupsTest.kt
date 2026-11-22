package com.degoos.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldGroupsTest {
    private val log = RecordingLog()
    private val groups = WorldGroups(log)

    @Test
    fun `world keeps its own sanitized name without groupers`() {
        assertEquals("my_world_", groups.label("my world!"))
    }

    @Test
    fun `grouper hit replaces the label`() {
        groups.set("a") { if (it.startsWith("floor-")) "floors" else null }
        assertEquals("floors", groups.label("floor-12"))
        assertEquals("hub", groups.label("hub"))
    }

    @Test
    fun `first non-null grouper wins in registration order`() {
        groups.set("a") { null }
        groups.set("b") { "second" }
        groups.set("c") { "third" }
        assertEquals("second", groups.label("x"))
    }

    @Test
    fun `group label is sanitized`() {
        groups.set("a") { "my floors/1" }
        assertEquals("my_floors_1", groups.label("x"))
    }

    @Test
    fun `throwing grouper counts as null and is logged once`() {
        groups.set("bad") { error("boom") }
        groups.set("good") { "ok" }
        repeat(3) { assertEquals("ok", groups.label("w")) }
        assertEquals(1, log.warnings.size)
        assertTrue(log.warnings.single().contains("bad"))
    }

    @Test
    fun `remove drops the grouper`() {
        groups.set("a") { "g" }
        groups.remove("a")
        groups.remove("never-there")
        assertEquals("w", groups.label("w"))
    }
}
