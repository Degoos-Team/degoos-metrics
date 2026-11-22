package com.degoos.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The per-label-set state of a counter or summary: validates the label values, caps the number of series and logs each
 * problem once. A wrong label count is a programming error and throws; a bad label value or an extra series is dropped.
 */
internal class SeriesStore<T : Any>(
    private val metricName: String,
    private val labelCount: Int,
    private val log: MetricsLog,
    private val create: () -> T,
) {
    private val series = ConcurrentHashMap<List<String>, T>()
    private val warnedInvalid = AtomicBoolean(false)
    private val warnedCap = AtomicBoolean(false)

    /** The state for [labelValues], or null when the series is refused. */
    fun forLabels(labelValues: Array<out String>): T? {
        require(labelValues.size == labelCount) { "$metricName takes $labelCount label values, got ${labelValues.size}" }
        val key = labelValues.toList()
        series[key]?.let { return it }
        if (!key.all(LabelRules::isValidLabelValue)) {
            if (warnedInvalid.compareAndSet(false, true)) log.warn("Metric $metricName: a label value is invalid and its samples are dropped")
            return null
        }
        return synchronized(this) { series[key] ?: addSeries(key) }
    }

    private fun addSeries(key: List<String>): T? {
        if (series.size >= LabelRules.MAX_SERIES_PER_METRIC) {
            if (warnedCap.compareAndSet(false, true)) {
                log.warn("Metric $metricName reached ${LabelRules.MAX_SERIES_PER_METRIC} series; further label sets are dropped")
            }
            return null
        }
        return create().also { series[key] = it }
    }

    fun snapshot(): Map<List<String>, T> = series.toMap()
}
