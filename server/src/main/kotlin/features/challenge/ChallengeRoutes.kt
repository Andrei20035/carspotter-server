package com.revio.server.features.challenge

import com.revio.server.core.util.getUuidClaim
import com.revio.server.core.util.toUuidOrNull
import com.revio.server.features.car_family.ICarFamilyService
import com.revio.server.features.challenge.dto.ChallengeContributionDTO
import com.revio.server.features.challenge.dto.ChallengeDTO
import com.revio.server.features.challenge.dto.ChallengeProgressDTO
import com.revio.server.features.challenge.dto.ChallengeProgressDetailDTO
import com.revio.server.features.challenge.dto.CurrentChallengeDTO
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.Instant

private suspend fun Challenge.toDTO(carFamilyService: ICarFamilyService): ChallengeDTO {
    val family = carFamilyService.getFamily(targetFamilyId)
    return ChallengeDTO(
        id = id,
        title = title,
        description = description,
        targetFamilyBrand = family?.brand.orEmpty(),
        targetFamilyName = family?.name.orEmpty(),
        requiredPosts = requiredPosts,
        rewardPoints = rewardPoints,
        startsAt = startsAt,
        endsAt = endsAt,
    )
}

private fun ParticipantProgress.toDTO() = ChallengeProgressDTO(
    contributionCount = contributionCount,
    rewardState = rewardState.name,
)

/**
 * Read-only, user-facing challenge endpoints (plan §5's "Utilizator" table). All instants are
 * UTC ISO-8601 — the client converts to the viewer's own timezone, never the server.
 */
fun Route.challengeRoutes() {
    val challengeService: IChallengeService by application.inject()
    val challengeProgressService: IChallengeProgressService by application.inject()
    val carFamilyService: ICarFamilyService by application.inject()

    route("/challenges") {
        authenticate("jwt") {
            get("/current") {
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val challenge = challengeService.findCurrentOrNext(Instant.now())
                if (challenge == null) {
                    call.respond(HttpStatusCode.OK, CurrentChallengeDTO(challenge = null, progress = null))
                    return@get
                }

                val progress = challengeProgressService.getUserProgress(challenge.id, userId)
                call.respond(
                    HttpStatusCode.OK,
                    CurrentChallengeDTO(
                        challenge = challenge.toDTO(carFamilyService),
                        progress = progress.toDTO(),
                    ),
                )
            }

            get("/{id}/progress") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))
                val userId = call.getUuidClaim("userId")
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                challengeService.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Challenge not found"))

                val progress = challengeProgressService.getUserProgress(id, userId)
                val contributions = challengeProgressService.listUserContributions(id, userId)

                call.respond(
                    HttpStatusCode.OK,
                    ChallengeProgressDetailDTO(
                        progress = progress.toDTO(),
                        contributions = contributions.map { ChallengeContributionDTO(postId = it.postId, createdAt = it.createdAt) },
                    ),
                )
            }
        }
    }
}
