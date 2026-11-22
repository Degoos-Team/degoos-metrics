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

class ServerHardeningTest {
    private val token = "h4rd3n-".repeat(5)
    private val log = RecordingLog()
    private val registry = MetricsRegistry(log).also { it.gauge("up_test", "Up.", emptyList()) { listOf(Sample(1.0)) } }
    private val server = MetricsServer("127.0.0.1", 0, token, registry, log).also { check(it.start()) }
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun stop() = server.stop()

    private fun send(path: String = "/metrics", method: String = "GET", auth: String? = "Bearer $token"): HttpResponse<String> {
        val body = if (method == "POST") HttpRequest.BodyPublishers.ofString("x") else HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.boundPort}$path")).method(method, body)
        auth?.let { builder.header("Authorization", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun closes(response: HttpResponse<String>) = response.headers().firstValue("Connection").orElse("").equals("close", true)

    @Test
    fun `error replies ask the client to close the connection`() {
        assertTrue(closes(send(auth = null)))
        assertTrue(closes(send("/nope")))
        assertTrue(closes(send(method = "POST")))
        assertFalse(closes(send()))
    }

    @Test
    fun `a collector failing with a linkage error does not break the page`() {
        registry.gauge("missing_dep", "h", emptyList()) { throw NoClassDefFoundError("com/other/Missing") }
        val response = send()
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("up_test 1"))
        assertFalse(response.body().contains("missing_dep"))
        assertEquals(1, log.warnings.count { it.contains("missing_dep") })
    }

    @Test
    fun `http timeouts are only set when unset`() {
        val saved = listOf(HttpTimeouts.REQUEST_PROPERTY, HttpTimeouts.RESPONSE_PROPERTY).associateWith { System.getProperty(it) }
        try {
            System.clearProperty(HttpTimeouts.REQUEST_PROPERTY)
            System.setProperty(HttpTimeouts.RESPONSE_PROPERTY, "99")
            HttpTimeouts.applyDefaults()
            assertEquals("10", System.getProperty(HttpTimeouts.REQUEST_PROPERTY))
            assertEquals("99", System.getProperty(HttpTimeouts.RESPONSE_PROPERTY))
        } finally {
            saved.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
        }
    }
}
