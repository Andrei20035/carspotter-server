package com.revio.server.features.report.dto

import com.revio.server.features.report.ReportReason
import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/posts/{postId}/reports`. The post id comes from the path and the
 * reporter id from the JWT — only the reason is supplied by the client.
 */
@Serializable
data class ReportRequestDTO(
    val reason: ReportReason,
)
