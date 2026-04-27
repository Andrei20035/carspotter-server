package testutils

import com.carspotter.features.like.LikeTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object LikeTestSeed {

    /**
     * Inserează un like direct în DB, ocolind service-ul.
     * Util pentru a pregăti starea inițială în teste.
     */
    fun insertLike(userId: UUID, postId: UUID): Unit = transaction {
        LikeTable.insert {
            it[LikeTable.userId] = userId
            it[LikeTable.postId] = postId
        }
        Unit
    }

    fun likeExists(userId: UUID, postId: UUID): Boolean = transaction {
        LikeTable
            .selectAll()
            .where { (LikeTable.userId eq userId) and (LikeTable.postId eq postId) }
            .any()
    }

    fun countLikes(postId: UUID): Long = transaction {
        LikeTable
            .selectAll()
            .where { LikeTable.postId eq postId }
            .count()
    }
}