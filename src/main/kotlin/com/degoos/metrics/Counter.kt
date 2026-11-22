package com.degoos.metrics

import java.util.concurrent.atomic.DoubleAdder

/** A monotonically increasing metric; safe to call from any thread. */
class Counter internal constructor(private val labelNames: List<String>, log: MetricsLog, name: String) {
    private val store = SeriesStore(name, labelNames.size, log) { DoubleAdder() }

    fun inc(vararg labelValues: String) = add(1.0, *labelValues)

    /** Adds [amount], which must be finite and not negative. */
    fun add(amount: Double, vararg labelValues: String) {
        require(amount.isFinite() && amount >= 0.0) { "A counter can only grow by a finite, non-negative amount" }
        store.forLabels(labelValues)?.add(amount)
    }

    internal fun lines(name: String): List<Line> =
        store.snapshot().map { (values, adder) -> Line(name, labelNames.zip(values), adder.sum()) }
}
