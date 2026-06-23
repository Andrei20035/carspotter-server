package com.carspotter.core.util

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
