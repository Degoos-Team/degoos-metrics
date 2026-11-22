package com.degoos.metrics.collectors

import com.degoos.metrics.MetricsRegistry
import com.degoos.metrics.Sample
import com.degoos.metrics.WorldGroups
import com.hypixel.hytale.server.core.universe.Universe

/**
 * `hytale_players`, `hytale_world_players{world}` (summed per group) and `hytale_world_count{world}`. Counts come from `ConcurrentHashMap` sizes (javap: `Universe`
 * and `World` keep their players in one), so they are read off-thread without locking.
 */
internal object PlayerCollector {
    fun register(registry: MetricsRegistry, groups: WorldGroups) {
        registry.gauge("hytale_players", "Players online on this server.", emptyList()) {
            listOf(Sample(Universe.get().playerCount.toDouble()))
        }
        registry.gauge("hytale_world_players", "Players online per world (summed over grouped worlds).", listOf("world")) {
            val worlds = Worlds.loaded()
            Worlds.samples(WorldAggregation.sum(worlds.map { Worlds.label(groups, it) to it.playerCount.toDouble() }))
        }
        registry.gauge("hytale_world_count", "Loaded worlds per world label.", listOf("world")) {
            Worlds.samples(WorldAggregation.count(Worlds.loaded().map { Worlds.label(groups, it) }))
        }
    }
}
