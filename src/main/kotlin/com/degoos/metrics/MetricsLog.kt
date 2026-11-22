package com.degoos.metrics

/** Where the plugin writes its few log lines; the Hytale logger in the server, a recorder in tests. Never pass the token. */
interface MetricsLog {
    fun info(message: String)
    fun warn(message: String)
}

/** Discards everything. */
object NoLog : MetricsLog {
    override fun info(message: String) = Unit
    override fun warn(message: String) = Unit
}
