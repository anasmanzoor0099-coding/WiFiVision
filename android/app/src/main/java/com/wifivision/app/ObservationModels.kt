package com.wifivision.app

import java.time.Instant
import java.util.Locale

/**
 * Validated device measurement location (represents where the phone measured,
 * never a confirmed physical router location).
 */
data class ObservationLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null
) {
    fun isValid(): Boolean {
        if (!latitude.isFinite() || latitude < -90.0 || latitude > 90.0) return false
        if (!longitude.isFinite() || longitude < -180.0 || longitude > 180.0) return false
        if (accuracyMeters != null && (!accuracyMeters.isFinite() || accuracyMeters < 0.0)) {
            return false
        }
        return true
    }
}

/**
 * Payload aligned with backend `observation.validator.js` and `NetworkObservation`.
 */
data class ObservationPayload(
    val ssid: String?,
    val bssid: String?,
    val signalDbm: Int,
    val frequencyMhz: Int,
    val band: String,
    val security: String,
    val signalQuality: Int?,
    val location: ObservationLocation?,
    val source: String = SOURCE_ANDROID,
    val observedAtEpochMs: Long
) {
    companion object {
        const val SOURCE_ANDROID = "android"

        val VALID_BANDS = setOf("2.4 GHz", "5 GHz", "6 GHz", "Unknown")
        val VALID_SECURITY = setOf("Open", "WEP", "WPA", "WPA2", "WPA3", "Unknown")

        private val PLACEHOLDER_SSIDS = setOf(
            "<unknown ssid>",
            "ssid unavailable",
            "not connected",
            "unknown"
        )

        private val BSSID_REGEX = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
        private const val UNAVAILABLE_BSSID = "02:00:00:00:00:00"

        fun sanitizeSsid(raw: String?): String? {
            val cleaned = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
            if (cleaned.isEmpty()) return null
            if (cleaned.lowercase(Locale.US) in PLACEHOLDER_SSIDS) return null
            return cleaned.take(100)
        }

        fun sanitizeBssid(raw: String?): String? {
            val cleaned = raw?.trim()?.uppercase(Locale.US) ?: return null
            if (cleaned == UNAVAILABLE_BSSID) return null
            if (!BSSID_REGEX.matches(cleaned)) return null
            return cleaned
        }

        fun classifyBand(frequencyMhz: Int): String {
            return when (frequencyMhz) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5900 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> "Unknown"
            }
        }

        fun parseSecurity(capabilities: String?): String {
            if (capabilities.isNullOrBlank()) return "Unknown"
            val upper = capabilities.uppercase(Locale.US)
            return when {
                upper.contains("SAE") || upper.contains("WPA3") -> "WPA3"
                upper.contains("WPA2") || upper.contains("RSN") -> "WPA2"
                upper.contains("WPA") -> "WPA"
                upper.contains("WEP") -> "WEP"
                upper.contains("OWE") || upper.contains("ESS") || upper.contains("IBSS") -> "Open"
                else -> "Unknown"
            }
        }
    }

    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (signalDbm !in -127..0) {
            errors.add("signalDbm must be between -127 and 0")
        }
        if (frequencyMhz <= 0) {
            errors.add("frequencyMhz must be positive")
        }
        if (band !in VALID_BANDS) {
            errors.add("Unsupported frequency band: $band")
        }
        if (security !in VALID_SECURITY) {
            errors.add("Unsupported security value: $security")
        }
        if (signalQuality != null && signalQuality !in 0..4) {
            errors.add("signalQuality must be between 0 and 4")
        }
        if (ssid != null) {
            val trimmed = ssid.trim()
            if (trimmed.isEmpty() || trimmed.length > 100 || trimmed.lowercase(Locale.US) in PLACEHOLDER_SSIDS) {
                errors.add("Invalid or placeholder SSID")
            }
        }
        if (bssid != null && (!BSSID_REGEX.matches(bssid) || bssid.equals(UNAVAILABLE_BSSID, ignoreCase = true))) {
            errors.add("Invalid or placeholder BSSID")
        }
        if (location != null && !location.isValid()) {
            errors.add("Invalid GPS measurement coordinates")
        }
        if (source != SOURCE_ANDROID) {
            errors.add("Invalid observation source")
        }
        if (observedAtEpochMs <= 0L) {
            errors.add("Invalid observedAt timestamp")
        }
        return errors
    }

    fun isValid(): Boolean = validationErrors().isEmpty()

    fun dedupeKey(): String {
        val networkIdentity = bssid?.lowercase(Locale.US)
            ?: "ssid:${ssid?.lowercase(Locale.US) ?: "hidden"}"
        return "$networkIdentity|$band"
    }

    fun toJsonString(): String {
        val fields = mutableListOf<String>()
        if (ssid != null) {
            fields.add("\"ssid\":${JsonSupport.quote(ssid)}")
        }
        if (bssid != null) {
            fields.add("\"bssid\":${JsonSupport.quote(bssid)}")
        }
        fields.add("\"signalDbm\":$signalDbm")
        fields.add("\"frequencyMhz\":$frequencyMhz")
        fields.add("\"band\":${JsonSupport.quote(band)}")
        fields.add("\"security\":${JsonSupport.quote(security)}")
        if (signalQuality != null) {
            fields.add("\"signalQuality\":$signalQuality")
        }
        if (location != null) {
            val locParts = mutableListOf(
                "\"latitude\":${location.latitude}",
                "\"longitude\":${location.longitude}"
            )
            if (location.accuracyMeters != null) {
                locParts.add("\"accuracyMeters\":${location.accuracyMeters}")
            }
            fields.add("\"location\":{${locParts.joinToString(",")}}")
        }
        fields.add("\"source\":${JsonSupport.quote(source)}")
        fields.add("\"observedAt\":${JsonSupport.quote(Instant.ofEpochMilli(observedAtEpochMs).toString())}")
        return "{${fields.joinToString(",")}}"
    }
}

/**
 * Pure-Kotlin JSON escaping/parsing helper so payload serialization and queue
 * persistence run identically on Android and in JVM unit tests without `org.json` stubs.
 */
internal object JsonSupport {
    fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun parseJson(input: String): Any? = Parser(input).parseValue()

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val ch = s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected character '$ch' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (peek('}')) {
                    pos++
                    return map
                }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek(']')) {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                if (peek(']')) {
                    pos++
                    return list
                }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < s.length) {
                val ch = s[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("Invalid unicode escape")
                                val hex = s.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape \\$esc")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseBoolean(): Boolean {
            return if (s.startsWith("true", pos)) {
                pos += 4
                true
            } else if (s.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (s.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Invalid null at $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && s[pos] in '0'..'9') pos++
            var isFloat = false
            if (pos < s.length && s[pos] == '.') {
                isFloat = true
                pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            val raw = s.substring(start, pos)
            return if (isFloat) {
                raw.toDouble()
            } else {
                raw.toLong()
            }
        }

        private fun skipWhitespace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(expected: Char): Boolean = pos < s.length && s[pos] == expected

        private fun expect(expected: Char) {
            if (pos >= s.length || s[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}
