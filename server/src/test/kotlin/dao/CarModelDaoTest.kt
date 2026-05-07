package dao

import com.carspotter.features.car_model.CarModelDAO
import com.carspotter.features.car_model.CarModelTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CarModelTestSeed
import testutils.TestDatabaseFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelDaoTest {

    private val dao = CarModelDAO()

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

    // ---------- getAllCarBrands ----------

    @Test
    fun `getAllCarBrands returns empty list when table is empty`() = runTest {
        val brands = dao.getAllCarBrands()
        assertTrue(brands.isEmpty())
    }

    @Test
    fun `getAllCarBrands returns distinct brands`() = runTest {
        // Insert multiple modele pe același brand pentru a verifica DISTINCT-ul.
        CarModelTestSeed.seedDefault() // 2 x bmw, 1 x audi, 1 x alfa romeo

        val brands = dao.getAllCarBrands()

        assertEquals(3, brands.size, "expected 3 distinct brands")
        assertEquals(setOf("alfa romeo", "audi", "bmw"), brands.toSet())
    }

    @Test
    fun `getAllCarBrands returns brands sorted alphabetically`() = runTest {
        // Insert deliberat în ordine random.
        CarModelTestSeed.insertModel("toyota", "corolla")
        CarModelTestSeed.insertModel("audi", "a3")
        CarModelTestSeed.insertModel("bmw", "m3")
        CarModelTestSeed.insertModel("alfa romeo", "giulia")

        val brands = dao.getAllCarBrands()

        assertEquals(listOf("alfa romeo", "audi", "bmw", "toyota"), brands)
    }

    // ---------- getCarModelsForBrand ----------

    @Test
    fun `getCarModelsForBrand returns models with id and model only`() = runTest {
        val seeded = CarModelTestSeed.seedDefault()

        val models = dao.getCarModelsForBrand("bmw")

        assertEquals(2, models.size)
        // Toate id-urile trebuie să fie non-null și să corespundă seed-ului.
        val seededBmwIds = setOf(seeded.bmwM3, seeded.bmwM4)
        val returnedIds = models.map { it.id }.toSet()
        assertEquals(seededBmwIds, returnedIds)
        // Toate modelele trebuie să aparțină BMW.
        assertEquals(setOf("m3", "m4"), models.map { it.model }.toSet())
    }

    @Test
    fun `getCarModelsForBrand returns models sorted alphabetically`() = runTest {
        // Insert intenționat în ordine inversă.
        CarModelTestSeed.insertModel("bmw", "x5")
        CarModelTestSeed.insertModel("bmw", "m3")
        CarModelTestSeed.insertModel("bmw", "i3")
        CarModelTestSeed.insertModel("bmw", "m5")

        val models = dao.getCarModelsForBrand("bmw")

        assertEquals(listOf("i3", "m3", "m5", "x5"), models.map { it.model })
    }

    @Test
    fun `getCarModelsForBrand returns empty list when brand does not exist`() = runTest {
        CarModelTestSeed.seedDefault()

        val models = dao.getCarModelsForBrand("nonexistent")

        assertTrue(models.isEmpty())
    }

    @Test
    fun `getCarModelsForBrand matches stored brand ignoring case`() = runTest {
        // Datele reale importate din CSV pot veni uppercase (ex: ARO).
        // Service-ul normalizează inputul la lowercase, iar DAO-ul trebuie să găsească brandul stocat.
        CarModelTestSeed.insertModel("ARO", "10 Series")
        CarModelTestSeed.insertModel("ARO", "24 Series")

        val models = dao.getCarModelsForBrand("aro")

        assertEquals(listOf("10 Series", "24 Series"), models.map { it.model })
    }

    // ---------- unique constraint ----------

    @Test
    fun `unique constraint blocks duplicate brand and model insertion`() {
        CarModelTestSeed.insertModel("bmw", "m3")

        assertThrows(Exception::class.java) {
            transaction {
                CarModelTable.insert {
                    it[brand] = "bmw"
                    it[model] = "m3"
                }
            }
        }
    }
}
