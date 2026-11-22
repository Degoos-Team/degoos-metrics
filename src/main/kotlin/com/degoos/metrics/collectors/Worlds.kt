package com.degoos.metrics.collectors

import com.degoos.metrics.Sample
import com.degoos.metrics.WorldGroups
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World

/** The loaded worlds, read without waiting for any world thread. */
internal object Worlds {
    /** A copy of the loaded worlds (`Universe.getWorlds()` is a live map), safe to iterate. */
    fun loaded(): List<World> = Universe.get().worlds.values.toList()

    /** The world's label value: its group when a plugin grouped it, otherwise its own name, sanitized. */
    fun label(groups: WorldGroups, world: World): String = groups.label(world.name)

    fun samples(values: Map<String, Double>): List<Sample> = values.map { (label, value) -> Sample(value, label) }
}
