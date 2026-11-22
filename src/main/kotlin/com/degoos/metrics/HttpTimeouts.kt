package com.degoos.metrics

/**
 * Bounds how long the JDK HttpServer waits for a client to send a request (`sun.net.httpserver.maxReqTime`) and to take
 * a response (`maxRspTime`), so a stalled connection cannot hold one of the two worker threads forever. These are
 * JVM-global system properties read when the server is created, so they are set only when nobody set them already.
 */
internal object HttpTimeouts {
    const val REQUEST_PROPERTY = "sun.net.httpserver.maxReqTime"
    const val RESPONSE_PROPERTY = "sun.net.httpserver.maxRspTime"
    const val REQUEST_SECONDS = "10"
    const val RESPONSE_SECONDS = "30"

    /** Call before creating the HttpServer. */
    fun applyDefaults() {
        setIfUnset(REQUEST_PROPERTY, REQUEST_SECONDS)
        setIfUnset(RESPONSE_PROPERTY, RESPONSE_SECONDS)
    }

    private fun setIfUnset(name: String, value: String) {
        if (System.getProperty(name) == null) System.setProperty(name, value)
    }
}
