package com.degoos.metrics

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsServerTest {
    private val token = "s3cret-".repeat(5)
    private val log = RecordingLog()
    private val registry = MetricsRegistry(log).also { it.gauge("up_test", "Up.", emptyList()) { listOf(Sample(1.0)) } }
    private val server = MetricsServer("127.0.0.1", 0, token, registry, log)
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun stop() = server.stop()

    private fun send(path: String = "/metrics", method: String = "GET", auth: String? = "Bearer $token"): HttpResponse<String> {
        check(server.boundPort > 0 || server.start())
        val body = if (method == "POST") HttpRequest.BodyPublishers.ofString("x") else HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.boundPort}$path")).method(method, body)
        auth?.let { builder.header("Authorization", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `serves metrics with the right token`() {
        val response = send()
        assertEquals(200, response.statusCode())
        assertEquals(Exposition.CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""))
        assertTrue(response.body().contains("up_test 1\n"))
    }

    @Test
    fun `rejects a missing or wrong token with 401`() {
        for (auth in listOf(null, "Bearer wrong", "Bearer ", token, "Basic $token", "Bearer ${token}x")) {
            val response = send(auth = auth)
            assertEquals(401, response.statusCode(), "auth=$auth")
            assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(""))
            assertFalse(response.body().contains("up_test"))
        }
    }

    @Test
    fun `other paths are 404 and other methods are 405`() {
        assertEquals(404, send("/").statusCode())
        assertEquals(404, send("/metrics/extra").statusCode())
        assertEquals(405, send(method = "POST").statusCode())
        assertEquals(401, send("/other", auth = null).statusCode())
    }

    @Test
    fun `a throwing collector does not break the page`() {
        registry.gauge("broken_test", "Broken.", emptyList()) { error("boom") }
        repeat(2) {
            val response = send()
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("up_test 1"))
            assertFalse(response.body().contains("broken_test"))
        }
        assertEquals(1, log.warnings.count { it.contains("broken_test") })
    }

    @Test
    fun `stop is idempotent and the port is released`() {
        send()
        server.stop()
        server.stop()
        assertEquals(-1, server.boundPort)
    }

    @Test
    fun `an unbindable address is reported and not thrown`() {
        val other = MetricsServer("127.0.0.1", 0, token, registry, log)
        assertTrue(other.start())
        val clash = MetricsServer("127.0.0.1", other.boundPort, token, registry, log)
        assertFalse(clash.start())
        assertTrue(log.warnings.any { it.contains("cannot listen") })
        assertTrue(log.warnings.none { it.contains(token) })
        other.stop()
    }
}
