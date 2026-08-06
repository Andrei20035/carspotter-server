package testutils

import com.revio.server.features.car_family.CarFamilyTable
import com.revio.server.features.car_model.CarModelTable
import com.revio.server.features.challenge.ChallengeStatus
import com.revio.server.features.challenge.ChallengeTable
import com.revio.server.features.post.PostSource
import com.revio.server.features.post.PostTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Seed standardizat pentru testele de challenge, pe modelul [CarModelTestSeed]. Inserează direct
 * în tabele (nu prin ChallengeService), ca testele DAO/serviciu să poată controla exact starea
 * de pornit fără să treacă prin validarea de creare.
 *
 * [seedCameraPost] există pentru că `challenge_contributions.post_id` are FK real către `posts`
 * — testele de contribuție au nevoie de un rând de postare adevărat, nu doar de un UUID.
 */
object ChallengeTestSeed {

    fun seedFamily(brand: String = "volkswagen", name: String = "Golf"): UUID = transaction {
        CarFamilyTable.insert {
            it[CarFamilyTable.brand] = brand
            it[CarFamilyTable.name] = name
        }[CarFamilyTable.id].value
    }

    fun seedModel(brand: String, model: String, familyId: UUID? = null): UUID = transaction {
        CarModelTable.insert {
            it[CarModelTable.brand] = brand
            it[CarModelTable.model] = model
            it[CarModelTable.familyId] = familyId
        }[CarModelTable.id].value
    }

    /**
     * Inserează un challenge direct, cu control total asupra stării — nu trece prin validările
     * lui `ChallengeService.createDraft`. Dacă [createdAt] e dat, suprascrie valoarea implicită a
     * coloanei, ca testele de ordonare/paginare să nu depindă de timpul de perete dintre inserturi.
     */
    fun seedChallenge(
        familyId: UUID,
        startsAt: Instant,
        endsAt: Instant,
        title: String = "Test challenge",
        status: ChallengeStatus = ChallengeStatus.SCHEDULED,
        requiredPosts: Int = 5,
        rewardPoints: Int = 300,
        adminTimezone: String = "Europe/Bucharest",
        createdAt: Instant? = null,
    ): UUID = transaction {
        val id = ChallengeTable.insert {
            it[ChallengeTable.title] = title
            it[ChallengeTable.targetFamilyId] = familyId
            it[ChallengeTable.requiredPosts] = requiredPosts
            it[ChallengeTable.rewardPoints] = rewardPoints
            it[ChallengeTable.startsAt] = startsAt.atOffset(ZoneOffset.UTC)
            it[ChallengeTable.endsAt] = endsAt.atOffset(ZoneOffset.UTC)
            it[ChallengeTable.adminTimezone] = adminTimezone
            it[ChallengeTable.status] = status
        }[ChallengeTable.id].value

        if (createdAt != null) {
            ChallengeTable.update({ ChallengeTable.id eq id }) {
                it[ChallengeTable.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
            }
        }
        id
    }

    /** O postare CAMERA cu [carModelId] setat (fără custom brand/model — respectă XOR-ul din DB). */
    fun seedCameraPost(ownerUserId: UUID, carModelId: UUID, points: Int = 10): UUID = transaction {
        PostTable.insert {
            it[PostTable.userId] = ownerUserId
            it[PostTable.imageKey] = "posts/test.jpg"
            it[PostTable.carModelId] = carModelId
            it[PostTable.postSource] = PostSource.CAMERA.name
            it[PostTable.points] = points
        }[PostTable.id].value
    }
}
