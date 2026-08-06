package dao

import com.revio.server.features.challenge.ChallengeContributionTable
import com.revio.server.features.challenge.ChallengeParticipantTable
import com.revio.server.features.challenge.ChallengeProgressDAO
import com.revio.server.features.challenge.ChallengeRewardLedgerTable
import com.revio.server.features.challenge.ContributionEvaluation
import com.revio.server.features.challenge.LedgerEntryKind
import com.revio.server.features.challenge.LedgerReason
import com.revio.server.features.challenge.RewardState
import com.revio.server.features.user.UserTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.ChallengeTestSeed
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * IChallengeProgressDAO — the coarse-grained transaction behind every challenge write path
 * (plan §4.3/§4.4/§4.5/§4.8). Covers the DAO-relevant slices of the plan's §8 test matrix: exact
 * window boundaries, model/family eligibility, the grant/revoke/re-grant cycle with its
 * award_epoch bookkeeping, mass revoke, and the two concurrency scenarios that motivated the
 * upsert-based participant creation (see ChallengeProgressDAO's KDoc).
 *
 * Status-based eligibility (DRAFT/CANCELLED challenges never contribute) is a
 * ChallengeProgressService-level concern (it goes through IChallengeDAO.findActive, which this
 * DAO's evaluatePostContribution does not call) — covered in ChallengeProgressServiceTest instead.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChallengeProgressDaoTest {

    private val dao = ChallengeProgressDAO()

    @BeforeAll
    fun setup() = TestDatabaseFactory.start()

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() = TestDatabaseFactory.cleanDatabase()

    // ---------- helpers ----------

    private fun spotScore(userId: UUID): Int = transaction {
        UserTable.select(UserTable.spotScore).where { UserTable.id eq userId }.single()[UserTable.spotScore]
    }

    private fun setSpotScore(userId: UUID, score: Int) = transaction {
        UserTable.update({ UserTable.id eq userId }) { it[UserTable.spotScore] = score }
    }

    private fun participantRow(challengeId: UUID, userId: UUID) = transaction {
        ChallengeParticipantTable.selectAll()
            .where { (ChallengeParticipantTable.challengeId eq challengeId) and (ChallengeParticipantTable.userId eq userId) }
            .singleOrNull()
    }

    private fun ledgerRows(challengeId: UUID, userId: UUID) = transaction {
        ChallengeRewardLedgerTable.selectAll()
            .where { (ChallengeRewardLedgerTable.challengeId eq challengeId) and (ChallengeRewardLedgerTable.userId eq userId) }
            .orderBy(ChallengeRewardLedgerTable.createdAt)
            .toList()
    }

    private fun contributionCount(challengeId: UUID): Int = transaction {
        ChallengeContributionTable.select(ChallengeContributionTable.id)
            .where { ChallengeContributionTable.challengeId eq challengeId }
            .count()
            .toInt()
    }

    private fun postIdOfOnlyContribution(userId: UUID): UUID = transaction {
        ChallengeContributionTable.select(ChallengeContributionTable.postId)
            .where { ChallengeContributionTable.userId eq userId }
            .single()[ChallengeContributionTable.postId]
    }

    private data class Fixture(val userId: UUID, val familyId: UUID, val modelId: UUID, val challengeId: UUID, val startsAt: Instant, val endsAt: Instant)

    private fun fixture(requiredPosts: Int = 5, rewardPoints: Int = 300, windowHours: Long = 2): Fixture {
        val user = CommentTestSeed.seedUser()
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val startsAt = now.minus(windowHours, ChronoUnit.HOURS)
        val endsAt = now.plus(windowHours, ChronoUnit.HOURS)
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = familyId, startsAt = startsAt, endsAt = endsAt, requiredPosts = requiredPosts, rewardPoints = rewardPoints,
        )
        return Fixture(user.userId, familyId, modelId, challengeId, startsAt, endsAt)
    }

    // ---------- A. exact window boundaries ----------

    @Test
    fun `post exactly at startsAt is eligible (semi-open interval, inclusive left)`() = runBlocking {
        val f = fixture()
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, f.startsAt)

        assertTrue(result.eligible)
        assertEquals(1, result.contributionCount)
    }

    @Test
    fun `post one millisecond before startsAt is not eligible`() = runBlocking {
        val f = fixture()
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, f.startsAt.minusMillis(1))

        assertFalse(result.eligible)
        assertEquals(0, contributionCount(f.challengeId))
    }

    @Test
    fun `post exactly at endsAt is not eligible (semi-open interval, exclusive right)`() = runBlocking {
        val f = fixture()
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, f.endsAt)

        assertFalse(result.eligible)
    }

    @Test
    fun `post one millisecond before endsAt is eligible`() = runBlocking {
        val f = fixture()
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, f.endsAt.minusMillis(1))

        assertTrue(result.eligible)
    }

    // ---------- B. model / family eligibility ----------

    @Test
    fun `model with no family assigned is not eligible`() = runBlocking {
        val f = fixture()
        val unassignedModel = ChallengeTestSeed.seedModel("volkswagen", "gol", familyId = null)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, unassignedModel)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, unassignedModel, Instant.now())

        assertFalse(result.eligible)
    }

    @Test
    fun `model from a different family is not eligible`() = runBlocking {
        val f = fixture()
        val otherFamily = ChallengeTestSeed.seedFamily(name = "Passat")
        val passat = ChallengeTestSeed.seedModel("volkswagen", "passat", otherFamily)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, passat)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, postId, passat, Instant.now())

        assertFalse(result.eligible)
    }

    @Test
    fun `a single-member family contributes correctly`() = runBlocking {
        val singleMemberFamily = ChallengeTestSeed.seedFamily(name = "Arteon")
        val onlyModel = ChallengeTestSeed.seedModel("volkswagen", "arteon", singleMemberFamily)
        val user = CommentTestSeed.seedUser()
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(
            familyId = singleMemberFamily, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 1,
        )
        val postId = ChallengeTestSeed.seedCameraPost(user.userId, onlyModel)

        val result = dao.evaluatePostContribution(challengeId, user.userId, postId, onlyModel, now)

        assertTrue(result.eligible)
        assertTrue(result.rewardGranted)
    }

    // ---------- C. contribution is independent of posts.points (daily-cap overflow) ----------

    @Test
    fun `a post with points=0 (over the daily camera cap) still counts as a contribution`() = runBlocking {
        val f = fixture(requiredPosts = 1)
        val overCapPost = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId, points = 0)

        val result = dao.evaluatePostContribution(f.challengeId, f.userId, overCapPost, f.modelId, Instant.now())

        assertTrue(result.eligible)
        assertTrue(result.rewardGranted)
        assertEquals(300, spotScore(f.userId), "The reward is granted in full even though the triggering post itself earned 0 normal points")
    }

    // ---------- D. grant / revoke / re-grant mechanics ----------

    @Test
    fun `reaching requiredPosts grants exactly once, with a THRESHOLD_REACHED ledger row at epoch 0`() = runBlocking {
        val f = fixture(requiredPosts = 3, rewardPoints = 300)
        setSpotScore(f.userId, 0)

        var lastResult = ContributionEvaluation(eligible = false)
        repeat(3) {
            val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
            lastResult = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
        }

        assertTrue(lastResult.rewardGranted)
        assertEquals(3, lastResult.contributionCount)
        assertEquals(300, spotScore(f.userId))

        val ledger = ledgerRows(f.challengeId, f.userId)
        assertEquals(1, ledger.size)
        assertEquals(LedgerEntryKind.GRANT, ledger[0][ChallengeRewardLedgerTable.kind])
        assertEquals(LedgerReason.THRESHOLD_REACHED, ledger[0][ChallengeRewardLedgerTable.reason])
        assertEquals(0, ledger[0][ChallengeRewardLedgerTable.awardEpoch])
        assertEquals(300, ledger[0][ChallengeRewardLedgerTable.nominalDelta])
        assertEquals(300, ledger[0][ChallengeRewardLedgerTable.appliedDelta])
    }

    @Test
    fun `below-threshold contributions do not grant`() = runBlocking {
        val f = fixture(requiredPosts = 5)
        repeat(4) {
            val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
            dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
        }

        assertEquals(RewardState.NONE, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
        assertEquals(0, spotScore(f.userId))
        assertTrue(ledgerRows(f.challengeId, f.userId).isEmpty())
    }

    @Test
    fun `re-evaluating the same postId is idempotent - no duplicate contribution, no double grant`() = runBlocking {
        val f = fixture(requiredPosts = 1)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val first = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
        val second = dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())

        assertTrue(first.rewardGranted)
        assertFalse(second.rewardGranted, "Already GRANTED — the retried evaluation must not grant again")
        assertEquals(1, second.contributionCount, "ON CONFLICT DO NOTHING must not create a second contribution row")
        assertEquals(300, spotScore(f.userId))
        assertEquals(1, ledgerRows(f.challengeId, f.userId).size)
    }

    @Test
    fun `grant then drop below threshold via contribution removal revokes, bumping award_epoch`() = runBlocking {
        val f = fixture(requiredPosts = 2)
        val posts = (1..2).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }
        posts.forEach { dao.evaluatePostContribution(f.challengeId, f.userId, it, f.modelId, Instant.now()) }
        assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])

        val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(posts[0]) { true } }

        assertEquals(1, reversals.size)
        assertTrue(reversals[0].rewardRevoked)
        assertEquals(1, reversals[0].contributionCount)
        assertEquals(RewardState.NONE, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
        assertEquals(1, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.awardEpoch])
        assertEquals(0, spotScore(f.userId))

        val ledger = ledgerRows(f.challengeId, f.userId)
        assertEquals(2, ledger.size)
        assertEquals(LedgerEntryKind.REVOKE, ledger[1][ChallengeRewardLedgerTable.kind])
        assertEquals(LedgerReason.CONTRIBUTION_REMOVED, ledger[1][ChallengeRewardLedgerTable.reason])
        assertEquals(0, ledger[1][ChallengeRewardLedgerTable.awardEpoch])
    }

    @Test
    fun `deleting the first, an intermediate, or the last of several contributions has the same revoke effect`() = runBlocking {
        for (deleteIndex in listOf(0, 2, 4)) {
            TestDatabaseFactory.cleanDatabase()
            val f = fixture(requiredPosts = 5)
            val posts = (1..5).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }
            posts.forEach { dao.evaluatePostContribution(f.challengeId, f.userId, it, f.modelId, Instant.now()) }
            assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])

            val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(posts[deleteIndex]) { true } }

            assertTrue(reversals[0].rewardRevoked, "Revoking must not depend on which contribution (index $deleteIndex) was removed")
            assertEquals(4, reversals[0].contributionCount)
            assertEquals(0, spotScore(f.userId))
        }
    }

    @Test
    fun `removing a contribution while below threshold never writes a REVOKE (nothing was granted)`() = runBlocking {
        val f = fixture(requiredPosts = 5)
        val posts = (1..4).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }
        posts.forEach { dao.evaluatePostContribution(f.challengeId, f.userId, it, f.modelId, Instant.now()) }

        val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(posts[0]) { true } }

        assertFalse(reversals[0].rewardRevoked)
        assertEquals(3, reversals[0].contributionCount)
        assertTrue(ledgerRows(f.challengeId, f.userId).isEmpty())
    }

    @Test
    fun `re-adding an eligible post after a revoke re-grants with a fresh epoch, before endsAt`() = runBlocking {
        val f = fixture(requiredPosts = 2)
        val posts = (1..2).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }
        posts.forEach { dao.evaluatePostContribution(f.challengeId, f.userId, it, f.modelId, Instant.now()) }
        transaction { dao.removeContributionsForPostInCurrentTransaction(posts[0]) { true } }
        assertEquals(RewardState.NONE, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])

        val replacementPost = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        val result = dao.evaluatePostContribution(f.challengeId, f.userId, replacementPost, f.modelId, Instant.now())

        assertTrue(result.rewardGranted)
        assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
        assertEquals(1, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.awardEpoch])
        assertEquals(300, spotScore(f.userId))

        val ledger = ledgerRows(f.challengeId, f.userId)
        assertEquals(3, ledger.size, "GRANT(epoch 0), REVOKE(epoch 0), GRANT(epoch 1)")
        assertEquals(LedgerEntryKind.GRANT to 0, ledger[0][ChallengeRewardLedgerTable.kind] to ledger[0][ChallengeRewardLedgerTable.awardEpoch])
        assertEquals(LedgerEntryKind.REVOKE to 0, ledger[1][ChallengeRewardLedgerTable.kind] to ledger[1][ChallengeRewardLedgerTable.awardEpoch])
        assertEquals(LedgerEntryKind.GRANT to 1, ledger[2][ChallengeRewardLedgerTable.kind] to ledger[2][ChallengeRewardLedgerTable.awardEpoch])
    }

    @Test
    fun `grant-revoke cycle repeated 3 times produces 6 ledger rows and a correct final score`() = runBlocking {
        val f = fixture(requiredPosts = 1, rewardPoints = 100)
        setSpotScore(f.userId, 0)

        repeat(3) {
            val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
            dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
            assertEquals(100, spotScore(f.userId))
            transaction { dao.removeContributionsForPostInCurrentTransaction(postId) { true } }
            assertEquals(0, spotScore(f.userId))
        }

        assertEquals(6, ledgerRows(f.challengeId, f.userId).size)
        assertEquals(0, spotScore(f.userId))
        assertEquals(3, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.awardEpoch])
    }

    @Test
    fun `revoking after endsAt (allowRevoke=true) still revokes the reward`() = runBlocking {
        val f = fixture(requiredPosts = 1)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
        assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])

        // REVOKE_AFTER_END = true in production policy: revoke regardless of endsAt.
        val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(postId) { true } }

        assertTrue(reversals[0].rewardRevoked)
        assertEquals(RewardState.NONE, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
    }

    @Test
    fun `allowRevoke=false freezes an already-granted reward even though progress drops below threshold`() = runBlocking {
        val f = fixture(requiredPosts = 1)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())

        // Simulates a REVOKE_AFTER_END=false policy: the DAO stays policy-free (per its KDoc) and
        // does exactly what the caller's predicate says.
        val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(postId) { false } }

        assertFalse(reversals[0].rewardRevoked)
        assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
        assertEquals(300, spotScore(f.userId), "The reward must remain untouched when the caller's policy forbids revoking")
    }

    @Test
    fun `removing contributions for a post that made none is a no-op`() = runBlocking {
        val f = fixture()
        val unrelatedPost = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)

        val reversals = transaction { dao.removeContributionsForPostInCurrentTransaction(unrelatedPost) { true } }

        assertTrue(reversals.isEmpty())
    }

    @Test
    fun `revoking more than the current spot_score floors at zero, with the nominal delta preserved in the ledger`() = runBlocking {
        val f = fixture(requiredPosts = 1, rewardPoints = 300)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())
        setSpotScore(f.userId, 100) // simulate other point sources having been spent/reversed down to 100

        transaction { dao.removeContributionsForPostInCurrentTransaction(postId) { true } }

        assertEquals(0, spotScore(f.userId))
        val revoke = ledgerRows(f.challengeId, f.userId).last()
        assertEquals(-300, revoke[ChallengeRewardLedgerTable.nominalDelta])
        assertEquals(-100, revoke[ChallengeRewardLedgerTable.appliedDelta])
    }

    // ---------- F. revokeAllGrantedRewards (cancel / revoke-all engine) ----------

    @Test
    fun `revokeAllGrantedRewards revokes every GRANTED participant with the given reason`() = runBlocking {
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 1)

        val users = (1..3).map { CommentTestSeed.seedUser(username = "granted$it") }
        users.forEach { user ->
            val postId = ChallengeTestSeed.seedCameraPost(user.userId, modelId)
            dao.evaluatePostContribution(challengeId, user.userId, postId, modelId, now)
        }

        val revokedCount = dao.revokeAllGrantedRewards(challengeId, LedgerReason.CHALLENGE_CANCELLED)

        assertEquals(3, revokedCount)
        users.forEach { user ->
            assertEquals(RewardState.NONE, participantRow(challengeId, user.userId)!![ChallengeParticipantTable.rewardState])
            assertEquals(0, spotScore(user.userId))
            val revoke = ledgerRows(challengeId, user.userId).last()
            assertEquals(LedgerReason.CHALLENGE_CANCELLED, revoke[ChallengeRewardLedgerTable.reason])
        }
    }

    @Test
    fun `revokeAllGrantedRewards called again is idempotent and revokes nothing new`() = runBlocking {
        val f = fixture(requiredPosts = 1)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())

        val firstRun = dao.revokeAllGrantedRewards(f.challengeId, LedgerReason.ADMIN_REVOKE_ALL)
        val secondRun = dao.revokeAllGrantedRewards(f.challengeId, LedgerReason.ADMIN_REVOKE_ALL)

        assertEquals(1, firstRun)
        assertEquals(0, secondRun)
        assertEquals(1, ledgerRows(f.challengeId, f.userId).count { it[ChallengeRewardLedgerTable.kind] == LedgerEntryKind.REVOKE })
    }

    @Test
    fun `revokeAllGrantedRewards leaves NONE participants (never completed) untouched`() = runBlocking {
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 5)
        val incompleteUser = CommentTestSeed.seedUser(username = "incomplete")
        val postId = ChallengeTestSeed.seedCameraPost(incompleteUser.userId, modelId)
        dao.evaluatePostContribution(challengeId, incompleteUser.userId, postId, modelId, now)

        val revokedCount = dao.revokeAllGrantedRewards(challengeId, LedgerReason.ADMIN_REVOKE_ALL)

        assertEquals(0, revokedCount)
        assertEquals(1, participantRow(challengeId, incompleteUser.userId)!![ChallengeParticipantTable.contributionCount])
        assertTrue(ledgerRows(challengeId, incompleteUser.userId).isEmpty())
    }

    @Test
    fun `revokeAllGrantedRewards resumed after a partial revoke only processes what's left`() = runBlocking {
        val familyId = ChallengeTestSeed.seedFamily()
        val modelId = ChallengeTestSeed.seedModel("volkswagen", "golf r", familyId)
        val now = Instant.now()
        val challengeId = ChallengeTestSeed.seedChallenge(familyId = familyId, startsAt = now.minusSeconds(3600), endsAt = now.plusSeconds(3600), requiredPosts = 1)
        val users = (1..3).map { CommentTestSeed.seedUser(username = "resume$it") }
        users.forEach { user ->
            val postId = ChallengeTestSeed.seedCameraPost(user.userId, modelId)
            dao.evaluatePostContribution(challengeId, user.userId, postId, modelId, now)
        }

        // Simulate an interrupted first run: one participant already revoked "by hand".
        val firstUserPostId = postIdOfOnlyContribution(users[0].userId)
        transaction { dao.removeContributionsForPostInCurrentTransaction(firstUserPostId) { true } }
        assertEquals(RewardState.NONE, participantRow(challengeId, users[0].userId)!![ChallengeParticipantTable.rewardState])

        val resumed = dao.revokeAllGrantedRewards(challengeId, LedgerReason.ADMIN_REVOKE_ALL)

        assertEquals(2, resumed, "Only the two still-GRANTED participants should be processed")
        users.drop(1).forEach { user ->
            assertEquals(RewardState.NONE, participantRow(challengeId, user.userId)!![ChallengeParticipantTable.rewardState])
        }
    }

    // ---------- G. read-only progress ----------

    @Test
    fun `getProgress returns null for a user with no participant row`() = runBlocking {
        val f = fixture()
        assertNull(dao.getProgress(f.challengeId, f.userId))
    }

    @Test
    fun `getProgress reflects contributionCount and rewardState after contributions`() = runBlocking {
        val f = fixture(requiredPosts = 2)
        val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now())

        val progress = dao.getProgress(f.challengeId, f.userId)

        assertEquals(1, progress?.contributionCount)
        assertEquals(RewardState.NONE, progress?.rewardState)
    }

    @Test
    fun `listContributionsForUser returns contributions oldest-first`() = runBlocking {
        val f = fixture(requiredPosts = 10)
        val now = Instant.now()
        val first = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, first, f.modelId, now.minusSeconds(20))
        val second = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
        dao.evaluatePostContribution(f.challengeId, f.userId, second, f.modelId, now.minusSeconds(10))

        val contributions = dao.listContributionsForUser(f.challengeId, f.userId)

        assertEquals(listOf(first, second), contributions.map { it.postId })
    }

    // ---------- H. concurrency — real threads, not runTest's virtual-time dispatcher ----------

    @Test
    fun `5 concurrent posts from zero grant exactly once`() {
        val f = fixture(requiredPosts = 5)
        val postIds = (1..5).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }

        runBlocking(Dispatchers.IO) {
            postIds.map { postId ->
                async(Dispatchers.IO) { dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now()) }
            }.awaitAll()
        }

        assertEquals(5, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.contributionCount])
        assertEquals(RewardState.GRANTED, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.rewardState])
        assertEquals(300, spotScore(f.userId))
        assertEquals(1, ledgerRows(f.challengeId, f.userId).count { it[ChallengeRewardLedgerTable.kind] == LedgerEntryKind.GRANT })
    }

    @Test
    fun `2 concurrent posts reaching the threshold from 4 out of 5 grant exactly once`() {
        val f = fixture(requiredPosts = 5)
        // 4 sequential contributions first, so the concurrent pair races to be the 5th.
        repeat(4) {
            val postId = ChallengeTestSeed.seedCameraPost(f.userId, f.modelId)
            runBlocking { dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now()) }
        }
        val racingPosts = (1..2).map { ChallengeTestSeed.seedCameraPost(f.userId, f.modelId) }

        runBlocking(Dispatchers.IO) {
            racingPosts.map { postId ->
                async(Dispatchers.IO) { dao.evaluatePostContribution(f.challengeId, f.userId, postId, f.modelId, Instant.now()) }
            }.awaitAll()
        }

        assertEquals(6, participantRow(f.challengeId, f.userId)!![ChallengeParticipantTable.contributionCount])
        assertEquals(1, ledgerRows(f.challengeId, f.userId).count { it[ChallengeRewardLedgerTable.kind] == LedgerEntryKind.GRANT })
        assertEquals(300, spotScore(f.userId))
    }
}
