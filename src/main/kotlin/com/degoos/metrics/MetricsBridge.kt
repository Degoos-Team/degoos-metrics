package com.degoos.metrics

import java.util.function.Function
import java.util.function.ObjDoubleConsumer
import java.util.function.Supplier

/**
 * Registration API for plugins that must not compile against Degoos Metrics: look this class up by name at runtime and
 * call the static methods through `MethodHandles`. Every signature uses JDK types only. All calls go to the shared
 * [SharedMetrics.registry] and [SharedMetrics.worldGroups].
 */
object MetricsBridge {
    /** Bumped on an incompatible change to these signatures. */
    const val VERSION: Int = 1

    /** A gauge read at scrape time: label values to value. Throws [IllegalArgumentException] on an invalid or taken name. */
    @JvmStatic
    fun gauge(owner: String, name: String, help: String, labelNames: List<String>, read: Supplier<Map<List<String>, Double>>) {
        SharedMetrics.registry.gauge(name, help, labelNames.toList(), owner) {
            read.get().map { (labels, value) -> Sample(labels.toList(), value) }
        }
    }

    /** A counter; `accept(labelValues, amount)`. Bad input is dropped (logged once), never thrown to the caller. */
    @JvmStatic
    fun counter(owner: String, name: String, help: String, labelNames: List<String>): ObjDoubleConsumer<List<String>> {
        val registry = SharedMetrics.registry
        val counter = registry.counter(name, help, labelNames.toList(), owner)
        return ObjDoubleConsumer { labels, amount -> safely(registry, name) { counter.add(amount, *labels.toTypedArray()) } }
    }

    /** A summary; `accept(labelValues, observedValue)`. Bad input is dropped (logged once), never thrown to the caller. */
    @JvmStatic
    fun summary(owner: String, name: String, help: String, labelNames: List<String>): ObjDoubleConsumer<List<String>> {
        val registry = SharedMetrics.registry
        val summary = registry.summary(name, help, labelNames.toList(), owner)
        return ObjDoubleConsumer { labels, value -> safely(registry, name) { summary.observe(value, *labels.toTypedArray()) } }
    }

    /** Groups worlds under one `world` label: world name in, group label out, or null to keep the world's own label. */
    @JvmStatic
    fun groupWorlds(owner: String, grouper: Function<String, String?>) {
        SharedMetrics.worldGroups.set(owner, grouper)
    }

    /** Removes the owner's metrics and world groupers; always safe. */
    @JvmStatic
    fun unregister(owner: String) {
        SharedMetrics.registry.unregister(owner)
        SharedMetrics.worldGroups.remove(owner)
    }

    private inline fun safely(registry: MetricsRegistry, name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            if (!e.isRecoverable()) throw e
            registry.warnOnce(name, "bridge") { "A bridge update of $name was rejected (${e.javaClass.simpleName}: ${e.message}) and is dropped" }
        }
    }
}
