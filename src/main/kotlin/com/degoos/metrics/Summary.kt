package com.degoos.metrics

import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder

/** Counts observations and sums their values (`_count` and `_sum`); safe to call from any thread. */
class Summary internal constructor(private val labelNames: List<String>, log: MetricsLog, name: String) {
    private class Totals {
        val count = LongAdder()
        val sum = DoubleAdder()
    }

    private val store = SeriesStore(name, labelNames.size, log) { Totals() }

    /** Records one observation, which must be finite. */
    fun observe(value: Double, vararg labelValues: String) {
        require(value.isFinite()) { "A summary can only observe finite values" }
        val totals = store.forLabels(labelValues) ?: return
        totals.count.increment()
        totals.sum.add(value)
    }

    internal fun lines(name: String): List<Line> = store.snapshot().flatMap { (values, totals) ->
        val labels = labelNames.zip(values)
        listOf(Line("${name}_count", labels, totals.count.sum().toDouble()), Line("${name}_sum", labels, totals.sum.sum()))
    }
}
