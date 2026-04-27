package features.like

import kotlinx.serialization.Serializable

@Serializable
data class LikeStatusDTO(
    val liked: Boolean,
    val count: Long,
)