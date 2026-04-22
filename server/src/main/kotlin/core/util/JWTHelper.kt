package com.carspotter.core.util

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

fun ApplicationCall.getUuidClaim(claim: String): UUID? {
    return try {
        principal<JWTPrincipal>()
            ?.payload
            ?.getClaim(claim)
            ?.asString()
            ?.let(UUID::fromString)
    } catch (e: IllegalArgumentException) {
        null
    }
}