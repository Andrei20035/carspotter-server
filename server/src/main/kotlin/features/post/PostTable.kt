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
    val imageKey = text("image_path")
    val caption = text("description").nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    // Reverse-geocoded place name for the GPS coordinates above. Nullable: may be
    // unresolved on a slow connection or when the user has no location permission.
    val town = varchar("town", 100).nullable()
    val country = varchar("country", 100).nullable()
    val postSource = varchar("source", 16).default(PostSource.GALLERY.name)
    val createdAtTimezone = varchar("created_at_timezone", 64).nullable()
    val points = integer("points").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}
