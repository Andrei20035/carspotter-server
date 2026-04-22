package com.carspotter.features.user_car

import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object UserCarTable : UUIDTable("users_cars") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val carModelId = uuid("car_model_id").references(CarModelTable.id, onDelete = ReferenceOption.CASCADE)
    val imagePath = text("image_path").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}