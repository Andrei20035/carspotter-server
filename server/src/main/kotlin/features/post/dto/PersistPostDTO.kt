package com.carspotter.features.post.dto

import java.util.UUID

class PersistPostDTO(
    val userId: UUID,
    val carModelId: UUID?,
    val customBrand: String?,
    val customModel: String?,
    val imageObjectKey: String,
    val latitude: Double?,
    val longitude: Double?,
    val caption: String?
)
