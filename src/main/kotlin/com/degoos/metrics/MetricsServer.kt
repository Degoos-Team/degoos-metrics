package com.degoos.metrics

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serves `GET /metrics` with a bearer token. Every request is authenticated first, so an unauthenticated client learns
 * nothing about which paths exist. [port] 0 binds an ephemeral port (tests); the plugin only passes validated settings.
 */
class MetricsServer(
    private val bind: String,
    private val port: Int,
    token: String,
    private val registry: MetricsRegistry,
    private val log: MetricsLog = NoLog,
) {
    private val tokenBytes = token.toByteArray(StandardCharsets.UTF_8)
    private var server: HttpServer? = null

    /** The port actually bound, or -1 when not running. */
    val boundPort: Int get() = server?.address?.port ?: -1

    /** Binds and starts serving; returns false (and logs why) when the address cannot be bound. */
    @Synchronized
    fun start(): Boolean {
        if (server != null) return true
        return try {
            val http = HttpServer.create(InetSocketAddress(bind, port), BACKLOG)
            http.createContext("/", ::handle)
            http.executor = Executors.newFixedThreadPool(THREADS, daemonThreads())
            http.start()
            server = http
            log.info("Serving metrics on $bind:${http.address.port}")
            true
        } catch (e: IOException) {
            log.warn("Metrics server cannot listen on $bind:$port (${e.javaClass.simpleName}: ${e.message})")
            false
        }
    }

    /** Stops serving; safe to call more than once. */
    @Synchronized
    fun stop() {
        val http = server ?: return
        server = null
        http.stop(0)
        (http.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            try {
                respond(it)
            } catch (e: Throwable) {
                if (!e.isRecoverable()) throw e
                log.warn("Metrics request failed (${e.javaClass.simpleName})")
                runCatching { reply(it, 500, "Internal Server Error\n", close = true) }
            }
        }
    }

    private fun respond(exchange: HttpExchange) {
        when {
            !authorized(exchange) -> {
                exchange.responseHeaders.add("WWW-Authenticate", "Bearer")
                reply(exchange, 401, "Unauthorized\n", close = true)
            }
            exchange.requestURI.path != "/metrics" -> reply(exchange, 404, "Not Found\n", close = true)
            exchange.requestMethod != "GET" -> {
                exchange.responseHeaders.add("Allow", "GET")
                reply(exchange, 405, "Method Not Allowed\n", close = true)
            }
            else -> reply(exchange, 200, Exposition.render(registry.collect()), Exposition.CONTENT_TYPE)
        }
    }

    private fun authorized(exchange: HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return false
        if (!header.startsWith(BEARER_PREFIX)) return false
        val presented = header.substring(BEARER_PREFIX.length).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(presented, tokenBytes)
    }

    private fun reply(exchange: HttpExchange, status: Int, body: String, contentType: String = "text/plain; charset=utf-8", close: Boolean = false) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        if (close) exchange.responseHeaders.set("Connection", "close")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun daemonThreads(): ThreadFactory {
        val count = AtomicInteger()
        return ThreadFactory { task -> Thread(task, "degoos-metrics-http-${count.incrementAndGet()}").apply { isDaemon = true } }
    }

    private companion object {
        const val THREADS = 2
        const val BACKLOG = 16
        const val BEARER_PREFIX = "Bearer "
    }
}
