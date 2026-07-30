package opsi.sman35jkt.gathra.data.common

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the RFC 3339 forms emitted by the NestJS API without relying on
 * java.time, which is unavailable on the app's API 24–25 devices.
 */
internal fun parseStrictIsoTimestamp(raw: String?): Long? {
    if (raw.isNullOrBlank() || raw.length > MAX_TIMESTAMP_LENGTH) return null
    return TIMESTAMP_PATTERNS.firstNotNullOfOrNull { pattern ->
        val formatter = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        formatter.parse(raw, position)
            ?.takeIf { position.index == raw.length && position.errorIndex < 0 }
            ?.time
    }
}

private val TIMESTAMP_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
)
private const val MAX_TIMESTAMP_LENGTH = 40
