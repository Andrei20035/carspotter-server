package testutils

import at.favre.lib.crypto.bcrypt.BCrypt
import com.carspotter.features.auth.AuthTable
import com.carspotter.features.comment.CommentTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * Helpers pentru a popula DB-ul cu users și posts înainte de testele de comments.
 * Tabelele care intervin sunt: auth_credentials → users → posts → comments.
 */
object CommentTestSeed {

    data class SeededUser(
        val authId: UUID,
        val userId: UUID,
        val username: String,
        val email: String,
    )

    data class SeededPost(
        val postId: UUID,
        val ownerUserId: UUID,
    )

    fun seedUser(
        username: String = "alice",
        email: String = "$username@example.com",
        profilePicturePath: String? = null,
    ): SeededUser = transaction {
        val authId = AuthTable.insert {
            it[AuthTable.email] = email
            it[AuthTable.password] = BCrypt.withDefaults().hashToString(4, "Passw0rd!".toCharArray())
            it[AuthTable.provider] = "REGULAR"
        }[AuthTable.id].value

        val userId = UserTable.insert {
            it[UserTable.authCredentialId] = authId
            it[UserTable.fullName] = username.replaceFirstChar { c -> c.uppercase() }
            it[UserTable.username] = username
            it[UserTable.country] = "RO"
            it[UserTable.birthDate] = LocalDate.of(1995, 1, 1)
            it[UserTable.profilePicturePath] = profilePicturePath
        }[UserTable.id].value

        SeededUser(authId, userId, username, email)
    }

    fun seedPost(ownerUserId: UUID, customBrand: String = "bmw", customModel: String = "m3"): SeededPost = transaction {
        val postId = PostTable.insert {
            it[PostTable.userId] = ownerUserId
            it[PostTable.imageKey] = "posts/test.jpg"
            // Folosim custom_brand/custom_model ca să nu trebuiască să creăm car_models.
            // Constraint chk_post_car_source acceptă această combinație.
            it[PostTable.carModelId] = null
            it[PostTable.customBrand] = customBrand
            it[PostTable.customModel] = customModel
        }[PostTable.id].value

        SeededPost(postId, ownerUserId)
    }

    fun insertComment(userId: UUID, postId: UUID, text: String): UUID = transaction {
        CommentTable.insert {
            it[CommentTable.userId] = userId
            it[CommentTable.postId] = postId
            it[CommentTable.commentText] = text
        }[CommentTable.id].value
    }
}
