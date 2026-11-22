package com.degoos.metrics

/**
 * Reads and writes a flat JSON object of strings, numbers, booleans and nulls, which is all `metrics.json` needs, so the
 * plugin carries no JSON library. Nested values are refused.
 */
internal object FlatJson {
    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Map<String, Any?> = Reader(text).readObject()

    fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun readObject(): Map<String, Any?> {
            skipSpace()
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return finish(result)
            }
            while (true) {
                skipSpace()
                val key = readString()
                skipSpace()
                expect(':')
                skipSpace()
                result[key] = readValue()
                skipSpace()
                when (next()) {
                    ',' -> continue
                    '}' -> return finish(result)
                    else -> fail("expected , or }")
                }
            }
        }

        private fun finish(result: Map<String, Any?>): Map<String, Any?> {
            skipSpace()
            if (pos != text.length) fail("unexpected text after the object")
            return result
        }

        private fun readValue(): Any? = when (val c = peek()) {
            '"' -> readString()
            '{', '[' -> fail("nested values are not supported")
            else -> if (c == '-' || c.isDigit()) readNumber() else readLiteral()
        }

        private fun readLiteral(): Any? {
            for ((word, value) in LITERALS) {
                if (text.startsWith(word, pos)) {
                    pos += word.length
                    return value
                }
            }
            return fail("unexpected value")
        }

        private fun readNumber(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            return text.substring(start, pos).toDoubleOrNull() ?: fail("invalid number")
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = next()
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(readEscape())
                    else -> out.append(c)
                }
            }
        }

        private fun readEscape(): Char = when (val e = next()) {
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'b' -> '\b'
            'f' -> '\u000c'
            '"', '\\', '/' -> e
            'u' -> readUnicode()
            else -> fail("invalid escape")
        }

        private fun readUnicode(): Char {
            if (pos + 4 > text.length) fail("invalid escape")
            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("invalid escape")
            pos += 4
            return code.toChar()
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else fail("unexpected end")

        private fun next(): Char = peek().also { pos++ }

        private fun expect(c: Char) {
            if (next() != c) fail("expected $c")
        }

        private fun fail(message: String): Nothing = throw ParseException("$message at position $pos")
    }

    private val LITERALS = listOf("true" to true, "false" to false, "null" to null)
}
