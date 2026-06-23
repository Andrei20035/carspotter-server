package com.carspotter.config

import com.carspotter.core.error.AuthApiError
import com.carspotter.core.error.AuthApiException
import com.carspotter.core.error.AuthErrorResponse
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond

fun Application.configureAuthStatusPages() {
    install(StatusPages) {
        exception<AuthApiException> { call, cause ->
            call.respond(
                cause.statusCode,
                AuthErrorResponse(
                    error = AuthApiError(
                        code = cause.code,
                        message = cause.message
                    )
                )
            )
        }
    }
}
