package service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.CredentialCreationException
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.GoogleUser
import com.carspotter.features.auth.IAuthDAO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AuthServiceTest {

    private fun newService(
        dao: IAuthDAO = mockk(relaxed = true),
        verifier: GoogleTokenVerifier = mockk(relaxed = true)
    ): AuthService = AuthService(dao, verifier)

    // ---------- createCredentials ----------

    @Test
    fun `createCredentials REGULAR hashes password and normalizes email`() = runTest {
        val dao = mockk<IAuthDAO>()
        val expectedId = UUID.randomUUID()
        coEvery { dao.getCredentialsForLogin(any()) } returns null
        val saved = slot<AuthCredential>()
        coEvery { dao.createCredentials(capture(saved)) } returns expectedId

        val service = newService(dao = dao)

        val input = AuthCredential(
            email = "  Alice@Example.COM  ",
            password = "Passw0rd!",
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val id = service.createCredentials(input)

        assertEquals(expectedId, id)
        assertEquals("alice@example.com", saved.captured.email)
        assertNotNull(saved.captured.password)
        assertTrue(saved.captured.password != "Passw0rd!", "password should not be plain text")
        // BCrypt format starts with $2a$, $2b$ or $2y$
        assertTrue(saved.captured.password!!.startsWith("$2"))
        assertNull(saved.captured.googleId)
    }

    @Test
    fun `createCredentials REGULAR without password throws`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin(any()) } returns null

        val service = newService(dao = dao)

        val input = AuthCredential(
            email = "alice@example.com",
            password = null,
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.createCredentials(input) }
        }
    }

    @Test
    fun `createCredentials GOOGLE without googleId throws`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin(any()) } returns null

        val service = newService(dao = dao)

        val input = AuthCredential(
            email = "bob@example.com",
            password = null,
            provider = AuthProvider.GOOGLE,
            googleId = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.createCredentials(input) }
        }
    }

    @Test
    fun `createCredentials fails when email is already registered`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin("alice@example.com") } returns AuthCredential(
            id = UUID.randomUUID(),
            email = "alice@example.com",
            password = "whatever",
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val service = newService(dao = dao)

        val input = AuthCredential(
            email = "alice@example.com",
            password = "Passw0rd!",
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.createCredentials(input) }
        }
    }

    @Test
    fun `createCredentials wraps DAO IllegalStateException in CredentialCreationException`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin(any()) } returns null
        coEvery { dao.createCredentials(any()) } throws IllegalStateException("boom")

        val service = newService(dao = dao)

        val input = AuthCredential(
            email = "alice@example.com",
            password = "Passw0rd!",
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        assertThrows(CredentialCreationException::class.java) {
            runBlocking { service.createCredentials(input) }
        }
    }

    // ---------- regularLogin ----------

    @Test
    fun `regularLogin returns DTO for correct password`() = runTest {
        val dao = mockk<IAuthDAO>()
        val hashed = BCrypt.withDefaults().hashToString(12, "Passw0rd!".toCharArray())
        val id = UUID.randomUUID()
        coEvery { dao.getCredentialsForLogin("alice@example.com") } returns AuthCredential(
            id = id,
            email = "alice@example.com",
            password = hashed,
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val service = newService(dao = dao)
        val dto = service.regularLogin("  Alice@Example.COM  ", "Passw0rd!")

        assertNotNull(dto)
        assertEquals(id, dto!!.id)
        assertEquals("alice@example.com", dto.email)
    }

    @Test
    fun `regularLogin returns null for wrong password`() = runTest {
        val dao = mockk<IAuthDAO>()
        val hashed = BCrypt.withDefaults().hashToString(12, "Passw0rd!".toCharArray())
        coEvery { dao.getCredentialsForLogin("alice@example.com") } returns AuthCredential(
            id = UUID.randomUUID(),
            email = "alice@example.com",
            password = hashed,
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val service = newService(dao = dao)
        val dto = service.regularLogin("alice@example.com", "WrongPassword!")
        assertNull(dto)
    }

    @Test
    fun `regularLogin returns null for GOOGLE provider`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin("bob@example.com") } returns AuthCredential(
            id = UUID.randomUUID(),
            email = "bob@example.com",
            password = null,
            provider = AuthProvider.GOOGLE,
            googleId = "gid"
        )

        val service = newService(dao = dao)
        val dto = service.regularLogin("bob@example.com", "anything")
        assertNull(dto)
    }

    @Test
    fun `regularLogin returns null for unknown email`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsForLogin(any()) } returns null

        val service = newService(dao = dao)
        val dto = service.regularLogin("nobody@example.com", "whatever")
        assertNull(dto)
    }

    // ---------- googleLogin ----------

    @Test
    fun `googleLogin with invalid token returns null`() = runTest {
        val dao = mockk<IAuthDAO>(relaxed = true)
        val verifier = mockk<GoogleTokenVerifier>()
        coEvery { verifier.verify(any()) } returns null

        val service = newService(dao = dao, verifier = verifier)
        val dto = service.googleLogin("bad-token")
        assertNull(dto)
    }

    @Test
    fun `googleLogin with valid token and new account creates GOOGLE credential`() = runTest {
        val dao = mockk<IAuthDAO>()
        val verifier = mockk<GoogleTokenVerifier>()
        val newId = UUID.randomUUID()

        coEvery { verifier.verify("valid-token") } returns GoogleUser(
            email = "bob@example.com",
            googleId = "google-sub-123"
        )
        coEvery { dao.getCredentialsForLogin("bob@example.com") } returns null
        val saved = slot<AuthCredential>()
        coEvery { dao.createCredentials(capture(saved)) } returns newId

        val service = newService(dao = dao, verifier = verifier)
        val dto = service.googleLogin("valid-token")

        assertNotNull(dto)
        assertEquals("bob@example.com", dto!!.email)
        assertEquals(AuthProvider.GOOGLE, dto.provider)
        assertEquals(newId, dto.id)
        assertEquals("google-sub-123", saved.captured.googleId)
        assertNull(saved.captured.password)
    }

    @Test
    fun `googleLogin with existing REGULAR email returns null`() = runTest {
        val dao = mockk<IAuthDAO>()
        val verifier = mockk<GoogleTokenVerifier>()

        coEvery { verifier.verify(any()) } returns GoogleUser(
            email = "alice@example.com",
            googleId = "google-sub-999"
        )
        coEvery { dao.getCredentialsForLogin("alice@example.com") } returns AuthCredential(
            id = UUID.randomUUID(),
            email = "alice@example.com",
            password = "hashed",
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val service = newService(dao = dao, verifier = verifier)
        val dto = service.googleLogin("valid-token")
        assertNull(dto)
    }

    @Test
    fun `googleLogin with existing GOOGLE matching googleId returns DTO`() = runTest {
        val dao = mockk<IAuthDAO>()
        val verifier = mockk<GoogleTokenVerifier>()
        val existingId = UUID.randomUUID()

        coEvery { verifier.verify(any()) } returns GoogleUser(
            email = "bob@example.com",
            googleId = "gid-1"
        )
        coEvery { dao.getCredentialsForLogin("bob@example.com") } returns AuthCredential(
            id = existingId,
            email = "bob@example.com",
            password = null,
            provider = AuthProvider.GOOGLE,
            googleId = "gid-1"
        )

        val service = newService(dao = dao, verifier = verifier)
        val dto = service.googleLogin("valid-token")

        assertNotNull(dto)
        assertEquals(existingId, dto!!.id)
    }

    @Test
    fun `googleLogin with existing GOOGLE but different googleId returns null`() = runTest {
        val dao = mockk<IAuthDAO>()
        val verifier = mockk<GoogleTokenVerifier>()

        coEvery { verifier.verify(any()) } returns GoogleUser(
            email = "bob@example.com",
            googleId = "gid-NEW"
        )
        coEvery { dao.getCredentialsForLogin("bob@example.com") } returns AuthCredential(
            id = UUID.randomUUID(),
            email = "bob@example.com",
            password = null,
            provider = AuthProvider.GOOGLE,
            googleId = "gid-OLD"
        )

        val service = newService(dao = dao, verifier = verifier)
        val dto = service.googleLogin("valid-token")
        assertNull(dto)
    }

    // ---------- updatePassword ----------

    @Test
    fun `updatePassword verifies old password, hashes new one, calls DAO`() = runTest {
        val dao = mockk<IAuthDAO>()
        val credentialId = UUID.randomUUID()
        val oldHash = BCrypt.withDefaults().hashToString(12, "OldPass!1".toCharArray())

        coEvery { dao.getCredentialsById(credentialId) } returns AuthCredential(
            id = credentialId,
            email = "alice@example.com",
            password = oldHash,
            provider = AuthProvider.REGULAR,
            googleId = null
        )
        val newHashSlot = slot<String>()
        coEvery { dao.updatePassword(credentialId, capture(newHashSlot)) } returns 1

        val service = newService(dao = dao)
        val rows = service.updatePassword(credentialId, "OldPass!1", "NewPass!2")

        assertEquals(1, rows)
        assertTrue(newHashSlot.captured.startsWith("$2"))
        // ensure the new hash verifies against the new plain password
        val verifies = BCrypt.verifyer().verify("NewPass!2".toCharArray(), newHashSlot.captured).verified
        assertTrue(verifies)
        coVerify(exactly = 1) { dao.updatePassword(credentialId, any()) }
    }

    @Test
    fun `updatePassword rejects wrong old password`() = runTest {
        val dao = mockk<IAuthDAO>()
        val credentialId = UUID.randomUUID()
        val oldHash = BCrypt.withDefaults().hashToString(12, "OldPass!1".toCharArray())

        coEvery { dao.getCredentialsById(credentialId) } returns AuthCredential(
            id = credentialId,
            email = "alice@example.com",
            password = oldHash,
            provider = AuthProvider.REGULAR,
            googleId = null
        )

        val service = newService(dao = dao)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.updatePassword(credentialId, "WRONG-OLD", "NewPass!2")
            }
        }
    }

    @Test
    fun `updatePassword rejects GOOGLE accounts`() = runTest {
        val dao = mockk<IAuthDAO>()
        val credentialId = UUID.randomUUID()
        coEvery { dao.getCredentialsById(credentialId) } returns AuthCredential(
            id = credentialId,
            email = "bob@example.com",
            password = null,
            provider = AuthProvider.GOOGLE,
            googleId = "gid"
        )

        val service = newService(dao = dao)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.updatePassword(credentialId, "anything", "NewPass!2")
            }
        }
    }

    @Test
    fun `updatePassword throws when credential not found`() = runTest {
        val dao = mockk<IAuthDAO>()
        coEvery { dao.getCredentialsById(any()) } returns null

        val service = newService(dao = dao)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.updatePassword(UUID.randomUUID(), "x", "NewPass!2")
            }
        }
    }
}