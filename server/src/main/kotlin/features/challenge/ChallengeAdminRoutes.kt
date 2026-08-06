package com.revio.server.features.challenge

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import com.revio.server.core.util.getUuidClaim
import com.revio.server.core.util.toUuidOrNull
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@Serializable
data class CreateChallengeAdminRequest(
    val title: String,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class) val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    /** Local (no offset) ISO-8601 date-time, e.g. "2026-08-08T09:00:00" — the admin's wall clock. */
    val startsAtLocal: String,
    val endsAtLocal: String,
    /** IANA zone, e.g. "Europe/Bucharest". Validated strictly — see ChallengeService.createDraft. */
    val timezone: String,
)

@Serializable
data class UpdateChallengeTitleRequest(
    val title: String,
    val description: String? = null,
)

/** [confirmChallengeId] must repeat the path id — an explicit confirmation barrier for a retroactive mass revoke (plan §2.6b). */
@Serializable
data class RevokeAllRequest(
    @Serializable(with = UUIDSerializer::class) val confirmChallengeId: UUID,
)

@Serializable
data class RevokeResultDTO(val revokedCount: Int)

/** Keyset cursor for GET /admin/challenges — mirrors FeedCursorDTO's shape. */
@Serializable
data class ChallengeListCursorDTO(
    @Serializable(with = InstantSerializer::class) val lastCreatedAt: Instant,
    @Serializable(with = UUIDSerializer::class) val lastChallengeId: UUID,
)

@Serializable
data class ChallengeAdminPageDTO(
    val challenges: List<ChallengeAdminDTO>,
    val nextCursor: ChallengeListCursorDTO? = null,
    val hasMore: Boolean,
)

@Serializable
data class ChallengeAdminDTO(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val title: String,
    val description: String?,
    @Serializable(with = UUIDSerializer::class) val targetFamilyId: UUID,
    val requiredPosts: Int,
    val rewardPoints: Int,
    @Serializable(with = InstantSerializer::class) val startsAt: Instant,
    @Serializable(with = InstantSerializer::class) val endsAt: Instant,
    val adminTimezone: String,
    val status: String,
    @Serializable(with = UUIDSerializer::class) val createdBy: UUID?,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant?,
    @Serializable(with = InstantSerializer::class) val cancelledAt: Instant?,
)

private fun Challenge.toAdminDTO() = ChallengeAdminDTO(
    id = id,
    title = title,
    description = description,
    targetFamilyId = targetFamilyId,
    requiredPosts = requiredPosts,
    rewardPoints = rewardPoints,
    startsAt = startsAt,
    endsAt = endsAt,
    adminTimezone = adminTimezone,
    status = status.name,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    publishedAt = publishedAt,
    cancelledAt = cancelledAt,
)

/**
 * Parses a challenge request's local start/end wall-clock strings, shared by the create (POST)
 * and full-DRAFT-edit (PUT) handlers so the two error messages stay identical.
 */
private fun parseLocalWindow(startsAtLocal: String, endsAtLocal: String): Pair<LocalDateTime, LocalDateTime>? = try {
    LocalDateTime.parse(startsAtLocal) to LocalDateTime.parse(endsAtLocal)
} catch (e: DateTimeParseException) {
    null
}

/**
 * Admin CRUD/lifecycle for challenges. Gated by the "admin" JWT realm (config/Security.kt),
 * which only accepts a token whose isAdmin claim is true — see Etapa 5's UserTable/AuthRoutes
 * changes for how that claim now reflects users.role.
 *
 * Endpoints intentionally NOT included here — no DAO/service support exists yet, tracked as
 * plan gaps (§9.E): aggregate participant/completion stats on the detail response,
 * GET /admin/challenges/{id}/participants, and POST /admin/challenges/{id}/reconcile.
 */
fun Route.challengeAdminRoutes() {
    val challengeService: IChallengeService by application.inject()

    route("/admin/challenges") {
        authenticate("admin") {
            post {
                val req = try {
                    call.receive<CreateChallengeAdminRequest>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                }

                val (startsAtLocal, endsAtLocal) = parseLocalWindow(req.startsAtLocal, req.endsAtLocal)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid startsAtLocal or endsAtLocal"))

                val createdBy = call.getUuidClaim("userId")

                try {
                    val challenge = challengeService.createDraft(
                        title = req.title,
                        description = req.description,
                        targetFamilyId = req.targetFamilyId,
                        requiredPosts = req.requiredPosts,
                        rewardPoints = req.rewardPoints,
                        startsAtLocal = startsAtLocal,
                        endsAtLocal = endsAtLocal,
                        timezone = req.timezone,
                        createdBy = createdBy,
                    )
                    call.respond(HttpStatusCode.Created, challenge.toAdminDTO())
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            // Keyset-paginated list, filterable by effective status (plan §9-E5). ?status, when
            // present, must name one of EffectiveChallengeStatus's values (DRAFT/SCHEDULED/
            // ACTIVE/ENDED/CANCELLED) — not the persisted ChallengeStatus, which can't tell
            // ACTIVE from a not-yet-started SCHEDULED challenge.
            get {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 0
                val cursorCreatedAt = call.request.queryParameters["cursorCreatedAt"]?.let {
                    runCatching { Instant.parse(it) }.getOrElse {
                        return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid cursorCreatedAt"))
                    }
                }
                val cursorId = call.request.queryParameters["cursorId"]?.let {
                    it.toUuidOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid cursorId"))
                }
                val statusFilter = call.request.queryParameters["status"]?.let { raw ->
                    runCatching { EffectiveChallengeStatus.valueOf(raw.uppercase()) }.getOrElse {
                        return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid status"))
                    }
                }

                try {
                    val page = challengeService.listChallenges(limit, cursorCreatedAt, cursorId, statusFilter)
                    call.respond(
                        HttpStatusCode.OK,
                        ChallengeAdminPageDTO(
                            challenges = page.challenges.map { it.toAdminDTO() },
                            nextCursor = if (page.hasMore) {
                                ChallengeListCursorDTO(page.nextCursorCreatedAt!!, page.nextCursorId!!)
                            } else {
                                null
                            },
                            hasMore = page.hasMore,
                        ),
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            // Full-field edit of a DRAFT challenge — every field createDraft accepts, revalidated
            // the same way (plan §9-E3). PUT (full replace), not PATCH: distinct from the
            // title/description-only edit below, which also applies once SCHEDULED.
            put("/{id}") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                val req = try {
                    call.receive<CreateChallengeAdminRequest>()
                } catch (e: BadRequestException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                }

                val (startsAtLocal, endsAtLocal) = parseLocalWindow(req.startsAtLocal, req.endsAtLocal)
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid startsAtLocal or endsAtLocal"))

                try {
                    val challenge = challengeService.updateDraft(
                        challengeId = id,
                        title = req.title,
                        description = req.description,
                        targetFamilyId = req.targetFamilyId,
                        requiredPosts = req.requiredPosts,
                        rewardPoints = req.rewardPoints,
                        startsAtLocal = startsAtLocal,
                        endsAtLocal = endsAtLocal,
                        timezone = req.timezone,
                    )
                    call.respond(HttpStatusCode.OK, challenge.toAdminDTO())
                } catch (e: ChallengeNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))
                } catch (e: ChallengeNotEditableException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Challenge is not editable")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            get("/{id}") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                val challenge = challengeService.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))

                call.respond(HttpStatusCode.OK, challenge.toAdminDTO())
            }

            patch("/{id}") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                val req = try {
                    call.receive<UpdateChallengeTitleRequest>()
                } catch (e: BadRequestException) {
                    return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                }

                try {
                    val challenge = challengeService.updateTitleAndDescription(id, req.title, req.description)
                    call.respond(HttpStatusCode.OK, challenge.toAdminDTO())
                } catch (e: ChallengeNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))
                } catch (e: ChallengeNotEditableException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (e.message ?: "Challenge is not editable")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            post("/{id}/publish") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                try {
                    val challenge = challengeService.publish(id)
                    call.respond(HttpStatusCode.OK, challenge.toAdminDTO())
                } catch (e: ChallengeNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))
                } catch (e: ChallengeOverlapException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to (e.message ?: "Challenge overlaps another scheduled challenge")),
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
                }
            }

            post("/{id}/cancel") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                try {
                    val revokedCount = challengeService.cancel(id)
                    call.respond(HttpStatusCode.OK, RevokeResultDTO(revokedCount))
                } catch (e: ChallengeNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))
                } catch (e: ChallengeAlreadyEndedException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Challenge has already ended; use /revoke-all instead"),
                    )
                }
            }

            // Retroactive remediation for a misconfigured challenge, regardless of ends_at (plan §2.6b).
            // Requires the path id to be repeated in the body as an explicit confirmation barrier.
            post("/{id}/revoke-all") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                val req = try {
                    call.receive<RevokeAllRequest>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                }

                if (req.confirmChallengeId != id) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "confirmChallengeId must match the challenge id in the path"),
                    )
                }

                try {
                    val revokedCount = challengeService.revokeAll(id)
                    call.respond(HttpStatusCode.OK, RevokeResultDTO(revokedCount))
                } catch (e: ChallengeNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))
                }
            }
        }
    }
}
