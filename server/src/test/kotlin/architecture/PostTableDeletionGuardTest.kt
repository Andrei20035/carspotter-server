package architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards against reintroducing a post-removal path that bypasses
 * ScoringDao.reverseAndDeletePost — the only call site allowed to run
 * `PostTable.deleteWhere`. Any other one would delete a post row without reversing its
 * points and without PostService.removePost's chance to reconcile challenge rewards first
 * (challenge_contributions.post_id is ON DELETE CASCADE — see plan §4.5/§9-A4). PostDAO
 * used to have a second, dead `deleteById` that did exactly this; it was removed in Etapa 6.
 */
class PostTableDeletionGuardTest {

    @Test
    fun `PostTable deleteWhere appears in exactly one production file`() {
        val sourceRoot = File("src/main/kotlin")
        check(sourceRoot.isDirectory) { "Expected ${sourceRoot.absolutePath} to exist" }

        val matches = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("PostTable.deleteWhere") }
            .map { it.relativeTo(sourceRoot).path.replace(File.separatorChar, '/') }
            .toList()

        assertEquals(
            listOf("features/scoring/ScoringDao.kt"),
            matches,
            "PostTable.deleteWhere must only be called from ScoringDao.reverseAndDeletePost; found it in: $matches",
        )
    }
}
