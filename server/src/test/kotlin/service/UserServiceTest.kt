package service

import com.carspotter.core.storage.LocalImageStorageService
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.user.UserNotFoundException
import com.carspotter.features.user.UserProfileAlreadyExistsException
import com.carspotter.features.user.UserService
import com.carspotter.features.user.UsernameAlreadyExistsException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import testutils.UserTestSeed
import java.nio.file.Path
import java.util.UUID

class UserServiceTest {

    private fun newService(dao: IUserDAO = mockk(relaxed = true)) = UserService(
        dao,
        LocalImageStorageService(Path.of("/tmp/user-service-test-uploads"), "http://localhost:8080"),
    )

    @Test
    fun `createUserProfile rejects blank username`() = runTest {
        val dao = mockk<IUserDAO>(relaxed = true)
        coEvery { dao.getUserByAuthCredentialId(any()) } returns null
        coEvery { dao.usernameExistsIgnoreCase(any()) } returns false
        val authCredentialId = UUID.randomUUID()
        val service = newService(dao)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "   "))
            }
        }
    }

    @Test
    fun `createUserProfile rejects username that is too short`() = runTest {
        val dao = mockk<IUserDAO>(relaxed = true)
        coEvery { dao.getUserByAuthCredentialId(any()) } returns null
        coEvery { dao.usernameExistsIgnoreCase(any()) } returns false
        val authCredentialId = UUID.randomUUID()
        val service = newService(dao)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "ab"))
            }
        }
    }

    @Test
    fun `createUserProfile rejects invalid username characters`() = runTest {
        val dao = mockk<IUserDAO>(relaxed = true)
        coEvery { dao.getUserByAuthCredentialId(any()) } returns null
        coEvery { dao.usernameExistsIgnoreCase(any()) } returns false
        val authCredentialId = UUID.randomUUID()
        val service = newService(dao)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "bad-name"))
            }
        }
    }

    @Test
    fun `createUserProfile normalizes username to lowercase trimmed before DAO insert`() = runTest {
        val dao = mockk<IUserDAO>()
        val authCredentialId = UUID.randomUUID()
        val capturedUser = slot<com.carspotter.features.user.User>()
        coEvery { dao.getUserByAuthCredentialId(authCredentialId) } returns null
        coEvery { dao.usernameExistsIgnoreCase("alice_1") } returns false
        coEvery { dao.createUser(capture(capturedUser)) } returns UUID.randomUUID()

        val service = newService(dao)
        service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "  Alice_1 "))

        assertEquals("alice_1", capturedUser.captured.username)
    }

    @Test
    fun `createUserProfile blocks duplicate username case-insensitive`() = runTest {
        val dao = mockk<IUserDAO>()
        val authCredentialId = UUID.randomUUID()
        coEvery { dao.getUserByAuthCredentialId(authCredentialId) } returns null
        coEvery { dao.usernameExistsIgnoreCase("alice") } returns true

        val service = newService(dao)
        assertThrows(UsernameAlreadyExistsException::class.java) {
            runBlocking {
                service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "Alice"))
            }
        }
    }

    @Test
    fun `createUserProfile prevents creating a second profile for same authCredentialId`() = runTest {
        val dao = mockk<IUserDAO>()
        val authCredentialId = UUID.randomUUID()
        coEvery { dao.getUserByAuthCredentialId(authCredentialId) } returns UserTestSeed.buildUser(authCredentialId, username = "alice")

        val service = newService(dao)
        assertThrows(UserProfileAlreadyExistsException::class.java) {
            runBlocking {
                service.createUserProfile(authCredentialId, UserTestSeed.buildUser(authCredentialId, username = "bob"))
            }
        }
    }

    @Test
    fun `updateProfilePicture rejects blank image path`() = runTest {
        val service = newService()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.updateProfilePicture(UUID.randomUUID(), "   ") }
        }
    }

    @Test
    fun `updateProfilePicture throws UserNotFoundException when user does not exist`() = runTest {
        val dao = mockk<IUserDAO>()
        val userId = UUID.randomUUID()
        coEvery { dao.updateProfilePicture(userId, "a.jpg") } returns 0

        val service = newService(dao)
        assertThrows(UserNotFoundException::class.java) {
            runBlocking { service.updateProfilePicture(userId, "/uploads/a.jpg") }
        }
    }

    @Test
    fun `updateProfilePicture trims path and reloads updated user`() = runTest {
        val dao = mockk<IUserDAO>()
        val userId = UUID.randomUUID()
        val authCredentialId = UUID.randomUUID()
        val updatedPath = slot<String>()
        val updatedUser = UserTestSeed.buildUser(authCredentialId, username = "alice", profilePicturePath = "a.jpg").copy(id = userId)

        coEvery { dao.updateProfilePicture(userId, capture(updatedPath)) } returns 1
        coEvery { dao.getUserById(userId) } returns updatedUser

        val service = newService(dao)
        val dto = service.updateProfilePicture(userId, "  /uploads/a.jpg  ")

        assertEquals("a.jpg", updatedPath.captured)
        assertEquals("http://localhost:8080/uploads/a.jpg", dto.profilePicturePath)
    }

    @Test
    fun `updateProfilePicture normalizes absolute uploads URL before storing`() = runTest {
        val dao = mockk<IUserDAO>()
        val userId = UUID.randomUUID()
        val authCredentialId = UUID.randomUUID()
        val updatedPath = slot<String>()
        val updatedUser = UserTestSeed.buildUser(
            authCredentialId,
            username = "alice",
            profilePicturePath = "profile-pictures/a.jpg",
        ).copy(id = userId)

        coEvery { dao.updateProfilePicture(userId, capture(updatedPath)) } returns 1
        coEvery { dao.getUserById(userId) } returns updatedUser

        val service = newService(dao)
        val dto = service.updateProfilePicture(userId, "http://10.0.2.2:8080/uploads/profile-pictures/a.jpg")

        assertEquals("profile-pictures/a.jpg", updatedPath.captured)
        assertEquals("http://localhost:8080/uploads/profile-pictures/a.jpg", dto.profilePicturePath)
    }
}
