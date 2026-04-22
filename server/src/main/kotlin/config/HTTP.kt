package com.carspotter.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // Minimum size in bytes to compress
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        // For production, specify allowed origins instead of anyHost()
        // allowHost("your-mobile-app-domain.com", schemes = listOf("https"))
        anyHost() // TODO: Replace it in production
        allowCredentials = true
        maxAgeInSeconds = 3600
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
}
