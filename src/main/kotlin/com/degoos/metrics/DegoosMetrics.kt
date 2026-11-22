package com.degoos.metrics

import com.degoos.kayle.KotlinPlugin
import com.degoos.metrics.collectors.GenericCollectors
import com.hypixel.hytale.server.core.plugin.JavaPluginInit

/**
 * Serves Prometheus metrics and lets other plugins register theirs through [registry]. Without a valid `metrics.json`
 * nothing is served, but the plugin stays loaded and the registry keeps working.
 *
 * The registry lives as long as the class does, so handles that dependent plugins hold stay valid across a restart of
 * this plugin; shutdown removes only this plugin's own (generic) collectors.
 */
@Suppress("unused")
class DegoosMetrics(init: JavaPluginInit) : KotlinPlugin(init) {
    private val log = object : MetricsLog {
        override fun info(message: String) = logger.atInfo().log("[Degoos Metrics] %s", message)
        override fun warn(message: String) = logger.atWarning().log("[Degoos Metrics] %s", message)
    }

    private var config: Result<MetricsConfig> = Result.success(MetricsConfig())
    private var server: MetricsServer? = null

    override fun setup() {
        SharedMetrics.forwardingLog.target = log
        config = MetricsConfig.loadOrCreate(dataDirectory.resolve(MetricsConfig.FILE_NAME))
    }

    override fun start() {
        GenericCollectors.register(registry, worldGroups)
        val settings = config.getOrElse {
            log.warn("${it.message}; serving no metrics")
            return
        }
        val problem = settings.problem()
        if (problem != null) {
            log.warn("${MetricsConfig.FILE_NAME}: $problem; serving no metrics")
            return
        }
        // JVM-global and only applied when unset: must happen before the HttpServer is created.
        HttpTimeouts.applyDefaults()
        server = MetricsServer(settings.bind, settings.port, settings.token, registry, log).also { it.start() }
    }

    override fun shutdown() {
        server?.stop()
        server = null
        registry.unregister(MetricsRegistry.DEFAULT_OWNER)
        SharedMetrics.forwardingLog.target = NoLog
        super.shutdown()
    }

    companion object {
        /**
         * The shared registry for game plugins, created with the class and never replaced. Register at start with your
         * own owner string and call `registry.unregister(owner)` at shutdown; unregistering is always safe. When Metrics
         * is an optional dependency, wrap the first access in try/catch for `NoClassDefFoundError`.
         */
        val registry: MetricsRegistry get() = SharedMetrics.registry

        /** World-label groupers: many short-lived worlds (one per floor) can share one `world` label. Lives as long as [registry]. */
        val worldGroups: WorldGroups get() = SharedMetrics.worldGroups
    }
}
