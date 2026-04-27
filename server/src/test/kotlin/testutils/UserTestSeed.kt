package testutils

import at.favre.lib.crypto.bcrypt.BCrypt
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.user.User
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

object UserTestSeed {
    data class SeededCredential(
        val authCredentialId: UUID,
        val email: String,
    )

    fun seedAuthCredential(
        email: String = "user@example.com",
        password: String = "Passw0rd!",
    ): SeededCredential = transaction {
        val authId = AuthTable.insert {
            it[AuthTable.email] = email
            it[AuthTable.password] = BCrypt.withDefaults().hashToString(4, password.toCharArray())
            it[AuthTable.provider] = "REGULAR"
        }[AuthTable.id].value

        SeededCredential(authCredentialId = authId, email = email)
    }

    fun seedUser(
        authCredentialId: UUID,
        username: String = "alice",
        fullName: String = "Alice",
        country: String = "RO",
        profilePicturePath: String? = null,
    ): UUID = transaction {
        UserTable.insert {
            it[UserTable.authCredentialId] = authCredentialId
            it[UserTable.fullName] = fullName
            it[UserTable.username] = username
            it[UserTable.country] = country
            it[UserTable.birthDate] = LocalDate.of(1995, 1, 1)
            it[UserTable.profilePicturePath] = profilePicturePath
        }[UserTable.id].value
    }

    fun buildUser(
        authCredentialId: UUID,
        username: String = "alice",
        fullName: String = "Alice",
        phoneNumber: String? = null,
        country: String = "RO",
        profilePicturePath: String? = null,
    ): User = User(
        authCredentialId = authCredentialId,
        profilePicturePath = profilePicturePath,
        fullName = fullName,
        phoneNumber = phoneNumber,
        birthDate = LocalDate.of(1995, 1, 1),
        username = username,
        country = country,
    )
}
