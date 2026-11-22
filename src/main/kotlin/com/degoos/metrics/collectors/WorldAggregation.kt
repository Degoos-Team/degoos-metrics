package com.degoos.metrics.collectors

/** Pure rules for merging per-world readings that share a label; keys keep first-seen order. */
internal object WorldAggregation {
    /** Total per label (players). */
    fun sum(readings: List<Pair<String, Double>>): Map<String, Double> = combine(readings) { a, b -> a + b }

    /** Highest value per label (target TPS, tick averages and maxima: the busiest member wins). */
    fun max(readings: List<Pair<String, Double>>): Map<String, Double> = combine(readings) { a, b -> maxOf(a, b) }

    /** Number of worlds per label. */
    fun count(labels: List<String>): Map<String, Double> = sum(labels.map { it to 1.0 })

    private fun combine(readings: List<Pair<String, Double>>, merge: (Double, Double) -> Double): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        for ((label, value) in readings) result.merge(label, value, merge)
        return result
    }
}
