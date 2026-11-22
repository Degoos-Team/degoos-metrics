package com.degoos.metrics

/** The naming and label rules shared by the registry and the generic collectors. */
object LabelRules {
    const val MAX_LABEL_VALUE = 64
    const val MAX_SERIES_PER_METRIC = 500

    private val NAME = Regex("[a-zA-Z_:][a-zA-Z0-9_:]*")
    private val LABEL_NAME = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    private val LABEL_VALUE = Regex("[A-Za-z0-9_.:-]{1,64}")
    private val INVALID_CHARS = Regex("[^A-Za-z0-9_.:-]")

    fun isValidName(name: String): Boolean = NAME.matches(name)

    fun isValidLabelName(name: String): Boolean = LABEL_NAME.matches(name) && !name.startsWith("__")

    fun isValidLabelValue(value: String): Boolean = LABEL_VALUE.matches(value)

    /** Replaces invalid characters with `_` and truncates to 64; an empty value becomes `_`. */
    fun sanitizeLabelValue(value: String): String =
        INVALID_CHARS.replace(value, "_").take(MAX_LABEL_VALUE).ifEmpty { "_" }
}
