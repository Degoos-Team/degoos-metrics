package com.degoos.metrics.collectors

import com.degoos.metrics.MetricsRegistry
import com.degoos.metrics.Sample
import java.lang.management.ManagementFactory

/** Process memory, container memory and uptime. Unreadable figures are not emitted. */
internal object ProcessCollector {
    private const val MILLIS_PER_SECOND = 1000.0

    fun register(registry: MetricsRegistry) {
        registry.gauge("hytale_process_resident_bytes", "Resident memory of the server process.", emptyList()) {
            single(ProcessMemoryReader.residentBytes())
        }
        registry.gauge("hytale_container_memory_bytes", "Memory used by the container (cgroup v2).", emptyList()) {
            single(ProcessMemoryReader.containerBytes())
        }
        registry.gauge("hytale_container_memory_limit_bytes", "Memory limit of the container, when it has one.", emptyList()) {
            single(ProcessMemoryReader.containerLimitBytes())
        }
        registry.gauge("hytale_uptime_seconds", "Seconds since the server process started.", emptyList()) {
            listOf(Sample(ManagementFactory.getRuntimeMXBean().uptime / MILLIS_PER_SECOND))
        }
    }

    private fun single(bytes: Long?): List<Sample> = listOfNotNull(bytes?.let { Sample(it.toDouble()) })
}
