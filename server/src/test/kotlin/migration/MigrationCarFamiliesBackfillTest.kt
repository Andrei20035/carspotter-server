package migration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.util.UUID

/**
 * Verifică migrația V23 (backfill al familiilor pentru întregul catalog, nu doar Volkswagen
 * Golf), izolat de suita partajată din [testutils.TestDatabaseFactory] — la fel ca
 * MigrationV21BackfillTest, pentru același motiv: cleanDatabase() ar goli imediat orice ar
 * produce V23. Fiecare test pornește propriul container, migrează doar până la V22
 * (car_families + car_models.family_id există, dar necompletat), apoi aplică V23 și verifică
 * rezultatul direct pe catalogul real semănat de V5.
 */
class MigrationCarFamiliesBackfillTest {

    private lateinit var container: PostgreSQLContainer<*>
    private lateinit var dataSource: HikariDataSource

    private fun startAtV22() {
        container = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("revio_migration_test")
            withUsername("test")
            withPassword("test")
            withReuse(false)
        }
        container.start()

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            driverClassName = "org.postgresql.Driver"
            username = container.username
            password = container.password
            maximumPoolSize = 5
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        dataSource = HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migrations")
            .target("22")
            .load()
            .migrate()

        Database.connect(dataSource)
    }

    private fun applyV23() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migrations")
            .target("23")
            .load()
            .migrate()
    }

    @AfterEach
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
        if (::container.isInitialized) container.stop()
    }

    // ---------- helpers ----------

    private val golfVariants = setOf(
        "Golf 3 Doors",
        "Golf 5 Doors",
        "Golf Alltrack",
        "Golf Cabrio",
        "Golf GTD Variant",
        "Golf GTI / GTD / GTE",
        "Golf GTI cabrio",
        "Golf Plus",
        "Golf R",
        "Golf R Cabrio",
        "Golf R Variant",
        "Golf Sportsvan",
        "Golf Variant",
        "e-Golf",
    )

    private val clioVariants = setOf(
        "Clio 3 Doors",
        "Clio 5 Doors",
        "Clio Estate",
        "Clio RS",
        "Clio Symbol / Thalia",
    )

    private fun esc(value: String) = value.replace("'", "''")

    private fun familyIdOf(brand: String, model: String): UUID? = transaction {
        exec("SELECT family_id FROM car_models WHERE brand = '${esc(brand)}' AND model = '${esc(model)}'") { rs ->
            if (rs.next()) rs.getString("family_id")?.let(UUID::fromString) else null
        }
    }

    private fun familyNameOf(brand: String, model: String): String? = transaction {
        exec(
            """
            SELECT cf.name AS name
              FROM car_models cm
              JOIN car_families cf ON cf.id = cm.family_id
             WHERE cm.brand = '${esc(brand)}' AND cm.model = '${esc(model)}'
            """.trimIndent()
        ) { rs -> if (rs.next()) rs.getString("name") else null }
    }

    private fun countCarModels(): Int = transaction {
        exec("SELECT COUNT(*) AS c FROM car_models") { rs -> rs.next(); rs.getInt("c") } ?: 0
    }

    private fun countCarFamilies(): Int = transaction {
        exec("SELECT COUNT(*) AS c FROM car_families") { rs -> rs.next(); rs.getInt("c") } ?: 0
    }

    private fun countModelsWithNullFamily(): Int = transaction {
        exec("SELECT COUNT(*) AS c FROM car_models WHERE family_id IS NULL") { rs -> rs.next(); rs.getInt("c") } ?: 0
    }

    private fun countModelsInFamily(familyId: UUID): Int = transaction {
        exec("SELECT COUNT(*) AS c FROM car_models WHERE family_id = '$familyId'") { rs -> rs.next(); rs.getInt("c") } ?: 0
    }

    /** Would only be non-zero if a bug let car_models.family_id point at a car_families row of a
     * different brand — the composite FK from V22 already forbids this at the database level,
     * this is a second, independent check on the actual data. */
    private fun countCrossBrandFamilyLinks(): Int = transaction {
        exec(
            """
            SELECT COUNT(*) AS c
              FROM car_models cm
              JOIN car_families cf ON cf.id = cm.family_id
             WHERE cm.brand <> cf.brand
            """.trimIndent()
        ) { rs -> rs.next(); rs.getInt("c") } ?: 0
    }

    // ---------- V23 backfill, run through Flyway ----------

    @Test
    fun `every car_models row has a family_id after V23`() {
        startAtV22()
        applyV23()

        assertEquals(0, countModelsWithNullFamily())
    }

    @Test
    fun `no family links models from a brand different than its own`() {
        startAtV22()
        applyV23()

        assertEquals(0, countCrossBrandFamilyLinks())
    }

    @Test
    fun `the full V5 catalog of 2928 models is present and every row has a family_id`() {
        startAtV22()
        applyV23()

        assertEquals(2928, countCarModels(), "Sanity check: the full V5 catalog must be present")
        assertEquals(0, countModelsWithNullFamily())
    }

    @Test
    fun `all 14 Golf variants, including e-Golf and the GTI-GTD-GTE alias, share the Volkswagen Golf family`() {
        startAtV22()
        applyV23()

        val familyId = familyIdOf("Volkswagen", "Golf 3 Doors")
        assertTrue(familyId != null, "The Volkswagen Golf family must have been created")
        assertEquals("Golf", familyNameOf("Volkswagen", "Golf 3 Doors"))

        golfVariants.forEach { model ->
            assertEquals(familyId, familyIdOf("Volkswagen", model), "Expected '$model' to be linked to the Golf family")
        }
        assertEquals(golfVariants.size, countModelsInFamily(familyId!!))
    }

    @Test
    fun `Volkswagen Gol has its own family, distinct from Golf`() {
        startAtV22()
        applyV23()

        val golFamilyId = familyIdOf("Volkswagen", "Gol")
        val golfFamilyId = familyIdOf("Volkswagen", "Golf 3 Doors")

        assertTrue(golFamilyId != null, "'Gol' must have been assigned its own family by the singleton sweep")
        assertNotEquals(golfFamilyId, golFamilyId, "'Gol' must not be linked to the Golf family")
        assertEquals("Gol", familyNameOf("Volkswagen", "Gol"))
    }

    @Test
    fun `all Renault Clio variants, including the Symbol-Thalia alias, share the Renault Clio family`() {
        startAtV22()
        applyV23()

        val familyId = familyIdOf("Renault", "Clio 3 Doors")
        assertTrue(familyId != null, "The Renault Clio family must have been created")
        assertEquals("Clio", familyNameOf("Renault", "Clio 3 Doors"))

        clioVariants.forEach { model ->
            assertEquals(familyId, familyIdOf("Renault", model), "Expected '$model' to be linked to the Clio family")
        }
        assertEquals(clioVariants.size, countModelsInFamily(familyId!!))
    }

    @Test
    fun `Renault Arkana gets its own singleton family with exactly one member`() {
        startAtV22()
        applyV23()

        val familyId = familyIdOf("Renault", "Arkana")
        assertTrue(familyId != null, "'Arkana' must have been assigned a family by the singleton sweep")
        assertEquals("Arkana", familyNameOf("Renault", "Arkana"))
        assertEquals(1, countModelsInFamily(familyId!!))
    }

    @Test
    fun `Renault Grand Scenic is not linked to the Scenic family`() {
        startAtV22()
        applyV23()

        val grandScenicFamilyId = familyIdOf("Renault", "Grand Scenic")
        val scenicFamilyId = familyIdOf("Renault", "Scenic")

        assertTrue(grandScenicFamilyId != null, "'Grand Scenic' must have been assigned a family")
        assertTrue(scenicFamilyId != null, "'Scenic' must have been assigned a family")
        assertNotEquals(scenicFamilyId, grandScenicFamilyId, "'Grand Scenic' must not be linked to the Scenic family")
        assertEquals("Grand Scenic", familyNameOf("Renault", "Grand Scenic"))
    }

    // ---------- idempotency of the migration's own SQL body ----------

    @Test
    fun `V23's raw SQL is idempotent - re-running it changes neither family count nor existing links`() {
        startAtV22()
        applyV23()

        val familyCountBefore = countCarFamilies()
        val golfFamilyIdBefore = familyIdOf("Volkswagen", "Golf 3 Doors")
        val clioFamilyIdBefore = familyIdOf("Renault", "Clio 3 Doors")
        val arkanaFamilyIdBefore = familyIdOf("Renault", "Arkana")
        val golFamilyIdBefore = familyIdOf("Volkswagen", "Gol")

        // Runs the migration's raw SQL a second time, bypassing Flyway's own "already applied"
        // bookkeeping — this is what actually exercises the file's ON CONFLICT DO NOTHING / WHERE
        // ... IN (...) idempotency, not just Flyway's version skip.
        val sql = File("src/main/resources/db/migrations/V23__backfill_car_families.sql").readText()
        transaction { exec(sql) }

        assertEquals(familyCountBefore, countCarFamilies(), "Re-running must not create additional families")
        assertEquals(golfFamilyIdBefore, familyIdOf("Volkswagen", "Golf 3 Doors"), "Golf family id must be stable across re-runs")
        assertEquals(clioFamilyIdBefore, familyIdOf("Renault", "Clio 3 Doors"), "Clio family id must be stable across re-runs")
        assertEquals(arkanaFamilyIdBefore, familyIdOf("Renault", "Arkana"), "Arkana family id must be stable across re-runs")
        assertEquals(golFamilyIdBefore, familyIdOf("Volkswagen", "Gol"), "Gol family id must be stable across re-runs")
        assertEquals(0, countModelsWithNullFamily())
    }
}
