package com.degoos.metrics

/** Renders metric families to the Prometheus text format 0.0.4. Pure: no clocks, no state. */
internal object Exposition {
    const val CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8"

    fun render(families: List<Family>): String {
        val out = StringBuilder()
        for (family in families.sortedBy { it.name }) {
            out.append("# HELP ").append(family.name).append(' ').append(escapeHelp(family.help)).append('\n')
            out.append("# TYPE ").append(family.name).append(' ').append(family.type.text).append('\n')
            for (line in family.lines.sortedWith(LINE_ORDER)) appendLine(out, line)
        }
        return out.toString()
    }

    fun formatValue(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "+Inf"
        value == Double.NEGATIVE_INFINITY -> "-Inf"
        value == Math.rint(value) && Math.abs(value) < MAX_EXACT_INTEGER -> value.toLong().toString()
        else -> value.toString()
    }

    fun escapeLabelValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun escapeHelp(help: String): String = help.replace("\\", "\\\\").replace("\n", "\\n")

    private const val MAX_EXACT_INTEGER = 1e15

    private val LINE_ORDER: Comparator<Line> = compareBy<Line> { it.name }.thenBy { labelKey(it.labels) }

    private fun labelKey(labels: List<Pair<String, String>>): String =
        labels.joinToString("\u0000") { "${it.first}=${it.second}" }

    private fun appendLine(out: StringBuilder, line: Line) {
        out.append(line.name)
        if (line.labels.isNotEmpty()) {
            out.append('{')
            line.labels.forEachIndexed { index, (name, value) ->
                if (index > 0) out.append(',')
                out.append(name).append("=\"").append(escapeLabelValue(value)).append('"')
            }
            out.append('}')
        }
        out.append(' ').append(formatValue(line.value)).append('\n')
    }
}
