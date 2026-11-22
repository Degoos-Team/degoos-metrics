package com.degoos.metrics.collectors

import kotlin.test.Test
import kotlin.test.assertEquals

class WorldAggregationTest {
    private val readings = listOf("floors" to 2.0, "hub" to 5.0, "floors" to 3.0, "floors" to 1.0)

    @Test
    fun `sum adds readings per label`() {
        assertEquals(mapOf("floors" to 6.0, "hub" to 5.0), WorldAggregation.sum(readings))
    }

    @Test
    fun `max keeps the highest reading per label`() {
        assertEquals(mapOf("floors" to 3.0, "hub" to 5.0), WorldAggregation.max(readings))
    }

    @Test
    fun `count tallies worlds per label in first seen order`() {
        val counts = WorldAggregation.count(listOf("floors", "hub", "floors"))
        assertEquals(mapOf("floors" to 2.0, "hub" to 1.0), counts)
        assertEquals(listOf("floors", "hub"), counts.keys.toList())
    }

    @Test
    fun `empty input gives empty output`() {
        assertEquals(emptyMap(), WorldAggregation.sum(emptyList()))
        assertEquals(emptyMap(), WorldAggregation.max(emptyList()))
        assertEquals(emptyMap(), WorldAggregation.count(emptyList()))
    }
}
