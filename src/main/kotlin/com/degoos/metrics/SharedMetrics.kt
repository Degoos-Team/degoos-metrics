package com.degoos.metrics

/**
 * The registry and world groups every plugin shares. They live here, apart from the plugin class, so they exist without
 * a running server (the plugin base class needs the Hytale runtime to load) and keep their state when Metrics restarts.
 */
internal object SharedMetrics {
    /** Hands log lines to the running plugin's logger; the registry outlives the plugin instance, so it logs through this. */
    class ForwardingLog : MetricsLog {
        @Volatile
        var target: MetricsLog = NoLog

        override fun info(message: String) = target.info(message)
        override fun warn(message: String) = target.warn(message)
    }

    val forwardingLog = ForwardingLog()
    val registry = MetricsRegistry(forwardingLog)
    val worldGroups = WorldGroups(forwardingLog)
}
