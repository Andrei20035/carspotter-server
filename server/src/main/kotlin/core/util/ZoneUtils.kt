package com.revio.server.core.util

import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZoneOffset

private val logger = LoggerFactory.getLogger("ZoneUtils")

/**
 * Resolve an IANA timezone string to a [ZoneId], falling back to UTC when
 * the value is null, blank, or unrecognised.
 */
fun resolveZone(tz: String?): ZoneId {
    if (tz.isNullOrBlank()) return ZoneOffset.UTC
    return try {
        ZoneId.of(tz)
    } catch (e: Exception) {
        logger.warn("Invalid timezone '{}', falling back to UTC", tz)
        ZoneOffset.UTC
    }
}

/**
 * Parses an IANA timezone string, rejecting rather than silently falling back to UTC.
 *
 * [resolveZone]'s silent-UTC fallback exists for reading already-persisted, best-effort client
 * timezones (posts.created_at_timezone, users.last_streak_timezone) and has 3 call sites relying
 * on that behavior — do not change it. Admin-entered challenge windows need the opposite: a typo
 * like "Europe/Bucuresti" must reject the request, not silently shift the challenge by hours.
 */
fun requireValidZone(tz: String?): ZoneId {
    require(!tz.isNullOrBlank()) { "Timezone must not be blank" }
    return try {
        ZoneId.of(tz)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid IANA timezone: '$tz'", e)
    }
}
