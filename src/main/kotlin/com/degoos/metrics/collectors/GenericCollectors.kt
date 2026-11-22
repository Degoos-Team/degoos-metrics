package com.degoos.metrics.collectors

import com.degoos.metrics.MetricsRegistry
import com.degoos.metrics.WorldGroups

/** Registers the `hytale_` metrics every server exposes. */
object GenericCollectors {
    fun register(registry: MetricsRegistry, groups: WorldGroups) {
        PlayerCollector.register(registry, groups)
        WorldTickCollector.register(registry, groups)
        JvmCollector.register(registry)
        ProcessCollector.register(registry)
    }
}
