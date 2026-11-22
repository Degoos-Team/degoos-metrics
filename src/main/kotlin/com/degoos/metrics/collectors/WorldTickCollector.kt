package com.degoos.metrics.collectors

import com.degoos.metrics.MetricsRegistry
import com.degoos.metrics.Sample
import com.degoos.metrics.WorldGroups
import com.hypixel.hytale.server.core.universe.world.World

/**
 * `hytale_world_tps_target{world}` and `hytale_world_tick_seconds{world,stat}`
 * (signatures read with javap, vanilla `/world perf` is the reference). Every `World` is a `TickingThread` whose
 * `getBufferedTickLengthMetricSet()` is a `HistoricMetric` of tick lengths in nanoseconds with periods 10 s, 1 min and
 * 5 min; index 0 (10 s) is used here, and `getTps()` is the target rate.
 *
 * Threading: the metric is read from the scrape thread without hopping onto the world thread; reading never blocks or
 * waits for any world. javap shows `HistoricMetric` has no locks or volatile fields: its arrays are final and fixed-size,
 * `add` (world thread) and `clear` (`/world perf reset`) write them in place and `calculateMax` walks the ring buffer up
 * to a plain `nextIndex`. Off-thread values may therefore be stale or mix two ticks. A world that cannot be read is
 * skipped, so one broken world does not hide the others.
 */
internal object WorldTickCollector {
    /** `HistoricMetric` period index of the 10 s window. */
    private const val SHORT_PERIOD = 0
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    fun register(registry: MetricsRegistry, groups: WorldGroups) {
        registry.gauge("hytale_world_tps_target", "Target ticks per second of each world (highest of grouped worlds).", listOf("world")) {
            Worlds.samples(WorldAggregation.max(Worlds.loaded().map { Worlds.label(groups, it) to it.tps.toDouble() }))
        }
        registry.gauge("hytale_world_tick_seconds", "Tick length over the last 10 seconds, average and maximum (busiest of grouped worlds).", listOf("world", "stat")) {
            val readings = Worlds.loaded().mapNotNull { world -> tickReading(world)?.let { Worlds.label(groups, world) to it } }
            val avg = WorldAggregation.max(readings.map { (label, tick) -> label to tick.first })
            val max = WorldAggregation.max(readings.map { (label, tick) -> label to tick.second })
            avg.map { (label, value) -> Sample(value, label, "avg") } + max.map { (label, value) -> Sample(value, label, "max") }
        }
    }

    /** (average, maximum) tick seconds, or null when the world cannot be read. */
    private fun tickReading(world: World): Pair<Double, Double>? = try {
        val ticks = world.bufferedTickLengthMetricSet
        ticks.getAverage(SHORT_PERIOD) / NANOS_PER_SECOND to ticks.calculateMax(SHORT_PERIOD) / NANOS_PER_SECOND
    } catch (e: Exception) {
        null
    }
}
