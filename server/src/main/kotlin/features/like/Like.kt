package com.carspotter.features.like

import java.sql.Timestamp

data class Like(
    val id: Int = 0,
    val userId: Int,
    val postId: Int,
    val createdAt: Timestamp? = null,
)