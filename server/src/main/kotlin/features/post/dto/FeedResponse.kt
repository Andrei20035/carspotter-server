package com.carspotter.features.post.dto

import com.carspotter.features.post.FeedCursor
import kotlinx.serialization.Serializable

@Serializable
data class FeedResponse(
    val posts: List<PostDTO>,
    val nextCursor: FeedCursor?,
    val hasMore: Boolean
)