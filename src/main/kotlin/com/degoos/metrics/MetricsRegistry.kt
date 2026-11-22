package com.degoos.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * The shared set of metrics, safe to use from any thread. Gauges and observed counters are callbacks evaluated at scrape
 * time; counters and summaries keep their own atomic state. Every registration names an [owner] (the plugin) so that
 * [unregister] can remove everything that plugin added.
 */
class MetricsRegistry(private val log: MetricsLog = NoLog) {
    private class Entry(val owner: String, val name: String, val help: String, val type: MetricType, val read: () -> List<Line>)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** A value read at scrape time; [read] must be quick and must not block or wait for a world thread. */
    fun gauge(name: String, help: String, labelNames: List<String>, owner: String = DEFAULT_OWNER, read: Supplier<List<Sample>>) =
        register(owner, name, help, MetricType.GAUGE, labelNames) { sampled(name, labelNames, read) }

    /** A counter whose total is kept elsewhere (for example a GC count) and read at scrape time. */
    fun observedCounter(name: String, help: String, labelNames: List<String>, owner: String = DEFAULT_OWNER, read: Supplier<List<Sample>>) =
        register(owner, name, help, MetricType.COUNTER, labelNames) { sampled(name, labelNames, read) }

    @JvmOverloads
    fun counter(name: String, help: String, labelNames: List<String> = emptyList(), owner: String = DEFAULT_OWNER): Counter {
        val counter = Counter(labelNames, log, name)
        register(owner, name, help, MetricType.COUNTER, labelNames) { counter.lines(name) }
        return counter
    }

    @JvmOverloads
    fun summary(name: String, help: String, labelNames: List<String> = emptyList(), owner: String = DEFAULT_OWNER): Summary {
        val summary = Summary(labelNames, log, name)
        register(owner, name, help, MetricType.SUMMARY, labelNames) { summary.lines(name) }
        return summary
    }

    /** Removes every metric [owner] registered; handles it still holds keep working but are no longer exposed. */
    fun unregister(owner: String) {
        entries.values.removeIf { it.owner == owner }
    }

    /** Evaluates every metric. A failing one is logged once, skipped, and the rest are still returned. */
    internal fun collect(): List<Family> = entries.values.sortedBy { it.name }.mapNotNull { entry ->
        try {
            Family(entry.name, entry.help, entry.type, entry.read())
        } catch (e: Throwable) {
            if (!e.isRecoverable()) throw e
            warnOnce(entry.name, "failed") { "Metric ${entry.name} failed (${e.javaClass.simpleName}: ${e.message}) and is skipped" }
            null
        }
    }

    private fun register(owner: String, name: String, help: String, type: MetricType, labelNames: List<String>, read: () -> List<Line>) {
        require(LabelRules.isValidName(name)) { "Invalid metric name: $name" }
        require(labelNames.all(LabelRules::isValidLabelName)) { "Invalid label name for $name" }
        require(labelNames.toSet().size == labelNames.size) { "Duplicate label name for $name" }
        require(owner.isNotBlank()) { "A metric needs an owner" }
        synchronized(entries) {
            val taken = namesOf(name, type).firstOrNull { clashes(it) }
            require(taken == null) { "Metric $name collides with the registered name $taken" }
            entries[name] = Entry(owner, name, help, type, read)
        }
    }

    /** The sample names a metric occupies: a summary also owns `_count` and `_sum`. */
    private fun namesOf(name: String, type: MetricType): List<String> =
        if (type == MetricType.SUMMARY) listOf(name, "${name}_count", "${name}_sum") else listOf(name)

    private fun clashes(candidate: String): Boolean =
        entries.values.any { namesOf(it.name, it.type).contains(candidate) }

    private fun sampled(name: String, labelNames: List<String>, read: Supplier<List<Sample>>): List<Line> {
        val cap = LabelRules.MAX_SERIES_PER_METRIC
        val samples = read.get()
        val wellFormed = samples.filter { it.labelValues.size == labelNames.size && it.labelValues.all(LabelRules::isValidLabelValue) }
        val valid = wellFormed.distinctBy { it.labelValues }
        if (valid.size < wellFormed.size) warnOnce(name, "duplicate") { "Metric $name returned duplicate label sets; only the first of each is kept" }
        if (wellFormed.size < samples.size) warnOnce(name, "invalid") { "Metric $name returned samples with invalid labels; they are dropped" }
        if (valid.size > cap) warnOnce(name, "cap") { "Metric $name returned more than $cap series; the extra ones are dropped" }
        return valid.take(cap).map { Line(name, labelNames.zip(it.labelValues), it.value) }
    }

    internal fun warnOnce(name: String, kind: String, message: () -> String) {
        if (warned.add("$name/$kind")) log.warn(message())
    }

    companion object {
        const val DEFAULT_OWNER = "degoos-metrics"
    }
}
