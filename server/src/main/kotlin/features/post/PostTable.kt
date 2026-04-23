package com.carspotter.features.post

import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object PostTable : UUIDTable("posts") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val carModelId = uuid("car_model_id").references(CarModelTable.id, onDelete = ReferenceOption.RESTRICT).nullable()
    val customBrand = varchar("custom_brand", 50).nullable()
    val customModel = varchar("custom_model", 80).nullable()
    val imagePath = text("image_path")
    val description = text("description").nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}