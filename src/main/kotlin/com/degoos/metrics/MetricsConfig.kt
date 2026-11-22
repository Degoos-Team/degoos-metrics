package com.degoos.metrics

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** The `metrics.json` settings. The token never appears in [toString] or in any log line. */
data class MetricsConfig(val bind: String = DEFAULT_BIND, val port: Int = 0, val token: String = "") {

    /** Why the server must not start, or null when the settings are usable. */
    fun problem(): String? = when {
        port !in 1..65535 -> "port must be between 1 and 65535 (it is $port)"
        token.length < MIN_TOKEN_LENGTH -> "token must be at least $MIN_TOKEN_LENGTH characters"
        bind.isBlank() -> "bind must not be empty"
        else -> null
    }

    override fun toString(): String = "MetricsConfig(bind=$bind, port=$port, token=<hidden>)"

    fun toJson(): String = "{\n  \"bind\": ${FlatJson.quote(bind)},\n  \"port\": $port,\n  \"token\": ${FlatJson.quote(token)}\n}\n"

    companion object {
        const val FILE_NAME = "metrics.json"
        const val DEFAULT_BIND = "0.0.0.0"
        const val MIN_TOKEN_LENGTH = 32

        /**
         * Reads [file], writing the defaults first when it is missing. A file that cannot be read or parsed fails with a
         * message that is safe to log.
         */
        fun loadOrCreate(file: Path): Result<MetricsConfig> {
            try {
                if (!Files.exists(file)) {
                    file.parent?.let(Files::createDirectories)
                    Files.writeString(file, MetricsConfig().toJson(), StandardCharsets.UTF_8)
                    return Result.success(MetricsConfig())
                }
                return parse(Files.readString(file, StandardCharsets.UTF_8))
            } catch (e: IOException) {
                return Result.failure(IllegalStateException("$FILE_NAME cannot be read or created: ${e.javaClass.simpleName}"))
            }
        }

        fun parse(text: String): Result<MetricsConfig> {
            val values = try {
                FlatJson.parse(text)
            } catch (e: FlatJson.ParseException) {
                return Result.failure(IllegalStateException("$FILE_NAME is not valid JSON: ${e.message}"))
            }
            val defaults = MetricsConfig()
            val bind = values["bind"] ?: defaults.bind
            val port = values["port"] ?: defaults.port.toDouble()
            val token = values["token"] ?: defaults.token
            if (bind !is String || token !is String || port !is Double || port != Math.rint(port) || port !in 0.0..65535.0) {
                return Result.failure(IllegalStateException("$FILE_NAME needs a text bind, a whole-number port and a text token"))
            }
            return Result.success(MetricsConfig(bind, port.toInt(), token))
        }
    }
}
