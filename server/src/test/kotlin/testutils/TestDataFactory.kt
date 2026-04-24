package testutils

import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import at.favre.lib.crypto.bcrypt.BCrypt

object TestDataFactory {

    fun regularCredential(
        email: String = "alice@example.com",
        plainPassword: String = "Passw0rd!",
        hashed: Boolean = true
    ): AuthCredential {
        val pw = if (hashed) {
            BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
        } else {
            plainPassword
        }
        return AuthCredential(
            email = email,
            password = pw,
            provider = AuthProvider.REGULAR,
            googleId = null
        )
    }

    fun googleCredential(
        email: String = "bob@example.com",
        googleId: String = "google-sub-123"
    ): AuthCredential = AuthCredential(
        email = email,
        password = null,
        provider = AuthProvider.GOOGLE,
        googleId = googleId
    )
}