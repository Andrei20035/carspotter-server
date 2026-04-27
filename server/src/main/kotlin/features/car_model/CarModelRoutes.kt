package com.carspotter.features.car_model

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.carModelRoutes() {
    val carModelService: ICarModelService by application.inject()

    route("/car-models") {
        get("/brands") {
            call.respond(HttpStatusCode.OK, carModelService.getAllCarBrands())
        }

        get("/brands/{brand}/models") {
            val brand = call.parameters["brand"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing brand parameter")
                )

            call.respond(HttpStatusCode.OK, carModelService.getCarModelsForBrand(brand))
        }
    }
}