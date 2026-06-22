package com.carspotter.features.post

import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.ResultRow
import java.time.Instant
import java.util.*

data class Post(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val username: String,
    // Author's profile picture object key from the users table. Resolved at DTO boundaries.
    val authorProfilePictureUrl: String? = null,
    val carModelId: UUID? = null,
    val brand: String,
    val model: String,
    val imageKey: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val town: String? = null,
    val country: String? = null,
    val caption: String? = null,
    val source: PostSource = PostSource.GALLERY,
    val createdAtTimezone: String? = null,
    val createdAt: Instant,
)

fun ResultRow.toPost(): Post {
    val carBrand = if (this[PostTable.carModelId] != null) {
        this[CarModelTable.brand]
    } else {
        this[PostTable.customBrand]
    }
    val carModel = if (this[PostTable.carModelId] != null) {
        this[CarModelTable.model]
    } else {
        this[PostTable.customModel]
    }

    return Post(
        id = this[PostTable.id].value,
        userId = this[PostTable.userId],
        username = this[UserTable.username],
        authorProfilePictureUrl = this[UserTable.profilePicturePath],
        carModelId = this[PostTable.carModelId],
        brand = carBrand ?: error("Post ${this[PostTable.id].value} is missing brand"),
        model = carModel ?: error("Post ${this[PostTable.id].value} is missing model"),
        imageKey = this[PostTable.imageKey],
        latitude = this[PostTable.latitude],
        longitude = this[PostTable.longitude],
        town = this[PostTable.town],
        country = this[PostTable.country],
        caption = this[PostTable.caption],
        source = PostSource.fromStringOrGallery(this[PostTable.postSource]),
        createdAtTimezone = this[PostTable.createdAtTimezone],
        createdAt = this[PostTable.createdAt],
    )
}
