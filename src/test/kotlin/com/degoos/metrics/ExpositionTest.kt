package com.degoos.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpositionTest {
    @Test
    fun `renders help type and sorted samples`() {
        val family = Family(
            "app_players", "Players online.", MetricType.GAUGE,
            listOf(
                Line("app_players", listOf("world" to "b"), 2.0),
                Line("app_players", listOf("world" to "a"), 1.5),
            ),
        )
        val expected = "# HELP app_players Players online.\n# TYPE app_players gauge\n" +
            "app_players{world=\"a\"} 1.5\napp_players{world=\"b\"} 2\n"
        assertEquals(expected, Exposition.render(listOf(family)))
    }

    @Test
    fun `families are sorted by name and a label-less sample has no braces`() {
        val z = Family("z_total", "z", MetricType.COUNTER, listOf(Line("z_total", emptyList(), 3.0)))
        val a = Family("a_value", "a", MetricType.GAUGE, listOf(Line("a_value", emptyList(), 1.0)))
        val text = Exposition.render(listOf(z, a))
        assertTrue(text.indexOf("a_value 1") in 0 until text.indexOf("z_total 3"))
    }

    @Test
    fun `escapes label values and help text`() {
        val family = Family("m", "line\\one\ntwo", MetricType.GAUGE, listOf(Line("m", listOf("l" to "a\\b\"c\nd"), 1.0)))
        val text = Exposition.render(listOf(family))
        assertTrue(text.contains("# HELP m line\\\\one\\ntwo\n"))
        assertTrue(text.contains("m{l=\"a\\\\b\\\"c\\nd\"} 1\n"))
    }

    @Test
    fun `renders special and integral doubles`() {
        assertEquals("NaN", Exposition.formatValue(Double.NaN))
        assertEquals("+Inf", Exposition.formatValue(Double.POSITIVE_INFINITY))
        assertEquals("-Inf", Exposition.formatValue(Double.NEGATIVE_INFINITY))
        assertEquals("42", Exposition.formatValue(42.0))
        assertEquals("0", Exposition.formatValue(0.0))
        assertEquals("-3", Exposition.formatValue(-3.0))
        assertEquals("1000000000", Exposition.formatValue(1e9))
        assertEquals("0.25", Exposition.formatValue(0.25))
    }
}
