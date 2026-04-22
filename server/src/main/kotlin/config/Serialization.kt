package com.carspotter.config

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

//    // Test route (can be removed in production)
//    routing {
//        get("/json/test") {
//            call.respond(HttpStatusCode.Unauthorized, MessageResponse("Serialization is working"))
//        }
//    }
//}
//
//@Serializable
//data class MessageResponse(val message: String)
