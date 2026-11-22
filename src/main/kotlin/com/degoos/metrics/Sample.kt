package com.degoos.metrics

/** One value of a gauge or observed counter; [labelValues] line up with the label names the metric was registered with. */
data class Sample(val labelValues: List<String>, val value: Double) {
    constructor(value: Double, vararg labelValues: String) : this(labelValues.toList(), value)
}

internal enum class MetricType(val text: String) { GAUGE("gauge"), COUNTER("counter"), SUMMARY("summary") }

/** One output line: a (possibly suffixed) sample name, its labels in order, and the value. */
internal data class Line(val name: String, val labels: List<Pair<String, String>>, val value: Double)

/** A metric as rendered: header data plus its lines. */
internal data class Family(val name: String, val help: String, val type: MetricType, val lines: List<Line>)
