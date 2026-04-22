package com.carspotter.features.car_model

import com.carspotter.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.carModelRoutes() {
    val carModelService: ICarModelService by application.inject()

    route("/car-models") {
        get {
            val models = carModelService.getAllCarModels()
            if (models.isNotEmpty())
                call.respond(models)
            else
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No car models found")
                )
        }
        get("/id") {
            val brand = call.request.queryParameters["brand"]
            val model = call.request.queryParameters["model"]

            if (brand.isNullOrBlank() || model.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing brand or model query parameter"))
            }

            val id = carModelService.getCarModelId(brand, model)
            if (id != null)
                call.respond(mapOf("id" to id))
            else
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car model not found"))
        }

        get("/brands") {
            try {
                val brands = carModelService.getAllCarBrands()

                if (brands.isNotEmpty()) {
                    call.respond(HttpStatusCode.OK, brands)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "No brands found"))
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to get car brands", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load brands"))
            }
        }


        get("/brands/{brand}/models") {
            val brand = call.parameters["brand"]

            if (brand.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing brand parameter"))
            }

            val models = carModelService.getCarModelsForBrand(brand)
            if (models.isNotEmpty())
                call.respond(models)

            else
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "No models found for brand $brand"))
        }

        get("/{modelId}") {
            val modelId = call.parameters["modelId"].toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing modelId"))

            val model = carModelService.getCarModelById(modelId)
            if (model != null)
                call.respond(model)
            else
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Car model with ID $modelId not found")
                )
        }

    }
}