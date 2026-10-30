package com.vermouthx.stocker.utils

/**
 * Minimal JSON reader for the flat payloads returned by the public quote APIs.
 * The plugin has no JSON library on the compile classpath.
 */
internal object StockerJson {

    fun root(json: String): Map<String, Any?> {
        val text = json.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) {
            return emptyMap()
        }
        val value = Parser(text).parseValue()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: emptyMap()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any? {
            skip()
            if (i >= s.length) {
                error("Unexpected end of JSON")
            }
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal(4, true)
                'f' -> literal(5, false)
                'n' -> literal(4, null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else error("Unexpected '${s[i]}' at $i")
            }
        }

        private fun literal(length: Int, value: Any?): Any? {
            i += length
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            i++
            val map = LinkedHashMap<String, Any?>()
            skip()
            if (i < s.length && s[i] == '}') {
                i++
                return map
            }
            while (i < s.length) {
                skip()
                val key = parseString()
                skip()
                expect(':')
                map[key] = parseValue()
                skip()
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $i")
                }
            }
            error("Unterminated object")
        }

        private fun parseArray(): List<Any?> {
            i++
            val list = ArrayList<Any?>()
            skip()
            if (i < s.length && s[i] == ']') {
                i++
                return list
            }
            while (i < s.length) {
                list.add(parseValue())
                skip()
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $i")
                }
            }
            error("Unterminated array")
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
            error("Unterminated string")
        }

        private fun escape(): Char {
            if (i >= s.length) {
                error("Bad escape")
            }
            return when (val c = s[i++]) {
                '"', '\\', '/' -> c
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val hex = s.substring(i, (i + 4).coerceAtMost(s.length))
                    i += 4
                    hex.toInt(16).toChar()
                }
                else -> c
            }
        }

        private fun parseNumber(): Double {
            val start = i
            if (s[i] == '-') {
                i++
            }
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            return s.substring(start, i).toDouble()
        }

        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            skip()
            if (i >= s.length || s[i] != c) {
                error("Expected '$c' at $i")
            }
            i++
        }
    }
}

internal fun Any?.asJsonMap(): Map<String, Any?>? {
    @Suppress("UNCHECKED_CAST")
    return this as? Map<String, Any?>
}

internal fun Any?.asJsonList(): List<*> = this as? List<*> ?: emptyList<Any?>()

internal fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

internal fun Any?.asText(): String? = this as? String

internal fun Any?.asInt(): Int? = asDouble()?.toInt()
