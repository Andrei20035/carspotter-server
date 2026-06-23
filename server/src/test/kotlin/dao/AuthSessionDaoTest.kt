package dao

import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.auth.session.AuthSessionDAO
import com.carspotter.features.auth.session.NewAuthSession
import com.carspotter.features.auth.session.RevokeReason
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.SessionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDataFactory
import testutils.TestDatabaseFactory
import testutils.UserTestSeed
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSessionDaoTest {

    private val authDao = AuthDAO()
    private val dao = AuthSessionDAO()

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private suspend fun createCredential(email: String = "alice@example.com"): UUID =
        authDao.createCredentials(TestDataFactory.regularCredential(email = email))

    // ONBOARDING ca default — nu necesită userId real în DB
    private fun newSession(
        credentialId: UUID,
        scope: SessionScope = SessionScope.ONBOARDING,
        userId: UUID? = null,
        hash: String = "a".repeat(64),
        idleExpiresAt: Instant = Instant.now().plusSeconds(2_592_000),
        absoluteExpiresAt: Instant = Instant.now().plusSeconds(15_552_000),
    ) = NewAuthSession(
        credentialId = credentialId,
        userId = userId,
        scope = scope,
        refreshTokenHash = hash,
        deviceId = null,
        deviceName = null,
        userAgent = null,
        ipAddress = null,
        idleExpiresAt = idleExpiresAt,
        absoluteExpiresAt = absoluteExpiresAt,
    )

    // ── createSession ─────────────────────────────────────────────────────────

    @Test
    fun `V13 creates auth_sessions with TIMESTAMPTZ columns and user foreign key`() {
        TestDatabaseFactory.dataSource().connection.use { connection ->
            val timestampColumns = connection.prepareStatement(
                """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'auth_sessions'
                  AND column_name IN (
                      'prev_rotated_at',
                      'created_at',
                      'last_used_at',
                      'idle_expires_at',
                      'absolute_expires_at'
                  )
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    buildMap {
                        while (rows.next()) {
                            put(rows.getString("column_name"), rows.getString("data_type"))
                        }
                    }
                }
            }

            assertEquals(5, timestampColumns.size)
            assertTrue(
                timestampColumns.values.all { it == "timestamp with time zone" },
                "all auth session timestamps must use TIMESTAMPTZ: $timestampColumns",
            )

            val userForeignKeyTarget = connection.prepareStatement(
                """
                SELECT ccu.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.constraint_schema = kcu.constraint_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                 AND tc.constraint_schema = ccu.constraint_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                  AND tc.table_name = 'auth_sessions'
                  AND kcu.column_name = 'user_id'
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next(), "auth_sessions.user_id foreign key is missing")
                    rows.getString("table_name")
                }
            }

            assertEquals("users", userForeignKeyTarget)
        }
    }

    @Test
    fun `createSession inserts and returns session with ACTIVE status`() = runTest {
        val credId = createCredential()
        val session = dao.createSession(newSession(credId))

        assertNotNull(session.id)
        assertEquals(credId, session.credentialId)
        assertEquals(SessionStatus.ACTIVE, session.status)
        assertEquals(SessionScope.ONBOARDING, session.scope)
        assertEquals(1, session.version)
        assertNull(session.revokedReason)
        assertNull(session.userId)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns session by id`() = runTest {
        val credId = createCredential()
        val created = dao.createSession(newSession(credId))

        val found = dao.findById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `findById returns null for unknown id`() = runTest {
        val found = dao.findById(UUID.randomUUID())
        assertNull(found)
    }

    // ── findByRefreshHash ─────────────────────────────────────────────────────

    @Test
    fun `findByRefreshHash returns session by hash`() = runTest {
        val credId = createCredential()
        val hash = "b".repeat(64)
        dao.createSession(newSession(credId, hash = hash))

        val found = dao.findByRefreshHash(hash)

        assertNotNull(found)
        assertEquals(hash, found!!.refreshTokenHash)
    }

    @Test
    fun `findByRefreshHash returns null for unknown hash`() = runTest {
        val found = dao.findByRefreshHash("0".repeat(64))
        assertNull(found)
    }

    // ── revokeActiveByCredential ──────────────────────────────────────────────

    @Test
    fun `revokeActiveByCredential revokes ACTIVE session and returns 1`() = runTest {
        val credId = createCredential()
        dao.createSession(newSession(credId, hash = "c".repeat(64)))

        val rows = dao.revokeActiveByCredential(credId, RevokeReason.SUPERSEDED)

        assertEquals(1, rows)
        val sessions = dao.listActiveSessions(credId)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `revokeActiveByCredential with exceptSessionId skips that session`() = runTest {
        val credId = createCredential()
        val session = dao.createSession(newSession(credId, hash = "d".repeat(64)))

        val rows = dao.revokeActiveByCredential(credId, RevokeReason.SUPERSEDED, exceptSessionId = session.id)

        assertEquals(0, rows)
        val active = dao.listActiveSessions(credId)
        assertEquals(1, active.size)
    }

    @Test
    fun `revokeActiveByCredential returns 0 when no active session exists`() = runTest {
        val credId = createCredential()
        val rows = dao.revokeActiveByCredential(credId, RevokeReason.LOGOUT)
        assertEquals(0, rows)
    }

    // ── revokeSession ─────────────────────────────────────────────────────────

    @Test
    fun `revokeSession marks session as REVOKED with correct reason`() = runTest {
        val credId = createCredential()
        val session = dao.createSession(newSession(credId, hash = "e".repeat(64)))

        val rows = dao.revokeSession(session.id, RevokeReason.LOGOUT)

        assertEquals(1, rows)
        val revoked = dao.findById(session.id)
        assertNotNull(revoked)
        assertEquals(SessionStatus.REVOKED, revoked!!.status)
        assertEquals(RevokeReason.LOGOUT, revoked.revokedReason)
    }

    @Test
    fun `revokeSession returns 0 for unknown id`() = runTest {
        val rows = dao.revokeSession(UUID.randomUUID(), RevokeReason.LOGOUT)
        assertEquals(0, rows)
    }

    // ── rotateRefreshToken ────────────────────────────────────────────────────

    @Test
    fun `rotateRefreshToken updates hash, prevHash, version and idleExpiresAt`() = runTest {
        val credId = createCredential()
        val oldHash = "f".repeat(64)
        val newHash = "g".repeat(64)
        val session = dao.createSession(newSession(credId, hash = oldHash))
        val newIdle = Instant.now().plusSeconds(3_000_000)

        val rotated = dao.rotateRefreshToken(
            sessionId = session.id,
            newHash = newHash,
            prevHash = oldHash,
            newVersion = 2,
            newIdleExpiresAt = newIdle,
        )

        assertEquals(newHash, rotated.refreshTokenHash)
        assertEquals(oldHash, rotated.prevTokenHash)
        assertEquals(2, rotated.version)
        assertNotNull(rotated.prevRotatedAt)
        // idleExpiresAt actualizat (toleranță 2 s pentru execuție)
        assertTrue(rotated.idleExpiresAt.epochSecond >= newIdle.epochSecond - 2)
    }

    // ── promoteToFull ─────────────────────────────────────────────────────────

    @Test
    fun `promoteToFull upgrades scope to FULL, sets userId and rotates token`() = runTest {
        val credId = createCredential()
        val oldHash = "h".repeat(64)
        val newHash = "i".repeat(64)
        val session = dao.createSession(newSession(credId, hash = oldHash))

        // promoteToFull necesită un userId real în tabela users
        val userId = UserTestSeed.seedUser(authCredentialId = credId, username = "alice_promote")

        val promoted = dao.promoteToFull(
            sessionId = session.id,
            userId = userId,
            newHash = newHash,
            newVersion = 2,
        )

        assertEquals(SessionScope.FULL, promoted.scope)
        assertEquals(userId, promoted.userId)
        assertEquals(newHash, promoted.refreshTokenHash)
        assertEquals(2, promoted.version)
    }

    // ── listActiveSessions ────────────────────────────────────────────────────

    @Test
    fun `listActiveSessions returns only ACTIVE sessions for credential`() = runTest {
        val credId = createCredential()
        val s1 = dao.createSession(newSession(credId, hash = "j".repeat(64)))

        // revocă s1, creează s2
        dao.revokeSession(s1.id, RevokeReason.SUPERSEDED)
        dao.createSession(newSession(credId, hash = "k".repeat(64)))

        val active = dao.listActiveSessions(credId)

        assertEquals(1, active.size)
        assertEquals("k".repeat(64), active[0].refreshTokenHash)
    }

    @Test
    fun `listActiveSessions returns empty list when no active sessions`() = runTest {
        val credId = createCredential()
        val active = dao.listActiveSessions(credId)
        assertTrue(active.isEmpty())
    }

    // ── constraint: cel mult o sesiune ACTIVE per credential ──────────────────

    @Test
    fun `inserting two ACTIVE sessions for same credential violates unique constraint`() = runTest {
        val credId = createCredential()
        dao.createSession(newSession(credId, hash = "l".repeat(64)))

        // A doua inserție ACTIVE pentru același credential trebuie să eșueze
        assertThrows(Exception::class.java) {
            runTest {
                dao.createSession(newSession(credId, hash = "m".repeat(64)))
            }
        }
    }

    @Test
    fun `credential row FOR UPDATE serializes concurrent transactions`() {
        val credId = runBlocking { createCredential("lock-test@example.com") }
        val firstLockAcquired = CountDownLatch(1)
        val releaseFirstLock = CountDownLatch(1)
        val secondLockAcquired = CountDownLatch(1)
        val secondEnteredTransaction = CountDownLatch(1)
        val secondAcquiredBeforeRelease = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit {
                transaction {
                    AuthTable
                        .selectAll()
                        .where { AuthTable.id eq credId }
                        .forUpdate()
                        .single()
                    firstLockAcquired.countDown()
                    check(releaseFirstLock.await(5, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the first row lock"
                    }
                }
            }

            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS), "first transaction did not acquire the lock")

            val second = executor.submit {
                transaction {
                    secondEnteredTransaction.countDown()
                    AuthTable
                        .selectAll()
                        .where { AuthTable.id eq credId }
                        .forUpdate()
                        .single()
                    if (releaseFirstLock.count > 0) {
                        secondAcquiredBeforeRelease.set(true)
                    }
                    secondLockAcquired.countDown()
                }
            }

            assertTrue(
                secondEnteredTransaction.await(5, TimeUnit.SECONDS),
                "second transaction did not start",
            )
            assertFalse(
                secondLockAcquired.await(300, TimeUnit.MILLISECONDS),
                "second transaction acquired a row locked by the first transaction",
            )

            releaseFirstLock.countDown()

            assertTrue(
                secondLockAcquired.await(5, TimeUnit.SECONDS),
                "second transaction did not acquire the lock after the first committed",
            )
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertFalse(secondAcquiredBeforeRelease.get())
        } finally {
            releaseFirstLock.countDown()
            executor.shutdownNow()
        }
    }
}
