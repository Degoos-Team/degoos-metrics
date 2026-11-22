package com.degoos.metrics.collectors

import com.degoos.metrics.LabelRules
import com.degoos.metrics.MetricsRegistry
import com.degoos.metrics.Sample
import java.lang.management.BufferPoolMXBean
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory

/** JVM figures from the platform MXBeans, which are thread-safe snapshots. */
internal object JvmCollector {
    fun register(registry: MetricsRegistry) {
        registry.gauge("hytale_jvm_memory_bytes", "JVM memory by area.", listOf("area")) { memory() }
        registry.gauge("hytale_jvm_buffer_pool_bytes", "Memory used by JVM buffer pools (direct, mapped).", listOf("pool")) { bufferPools() }
        registry.gauge("hytale_jvm_threads", "Live JVM threads.", emptyList()) {
            listOf(Sample(ManagementFactory.getThreadMXBean().threadCount.toDouble()))
        }
        registry.observedCounter("hytale_jvm_gc_collections_total", "Garbage collections run.", listOf("gc")) {
            collectors().filter { it.collectionCount >= 0 }.map { Sample(it.collectionCount.toDouble(), LabelRules.sanitizeLabelValue(it.name)) }
        }
        registry.observedCounter("hytale_jvm_gc_seconds_total", "Time spent in garbage collection.", listOf("gc")) {
            collectors().filter { it.collectionTime >= 0 }.map { Sample(it.collectionTime / MILLIS_PER_SECOND, LabelRules.sanitizeLabelValue(it.name)) }
        }
    }

    private const val MILLIS_PER_SECOND = 1000.0

    private fun memory(): List<Sample> {
        val bean = ManagementFactory.getMemoryMXBean()
        val heap = bean.heapMemoryUsage
        return listOfNotNull(
            Sample(heap.used.toDouble(), "heap_used"),
            Sample(heap.committed.toDouble(), "heap_committed"),
            heap.max.takeIf { it >= 0 }?.let { Sample(it.toDouble(), "heap_max") },
            Sample(bean.nonHeapMemoryUsage.used.toDouble(), "non_heap_used"),
        )
    }

    private fun bufferPools(): List<Sample> = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java)
        .filter { it.memoryUsed >= 0 }
        .map { Sample(it.memoryUsed.toDouble(), LabelRules.sanitizeLabelValue(it.name)) }

    private fun collectors(): List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans()
}
