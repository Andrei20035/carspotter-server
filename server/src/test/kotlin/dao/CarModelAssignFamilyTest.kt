package dao

import com.revio.server.features.car_family.CarFamilyTable
import com.revio.server.features.car_model.CarModelDAO
import com.revio.server.features.car_model.CarModelTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import java.util.UUID

/**
 * ICarModelDAO.assignToFamily: the coarse-grained, all-or-nothing operation behind
 * POST /admin/car-families/{id}/models (plan §9-E4). "All-or-nothing" is the property under
 * test throughout — a request with any problem must leave every row it touched unchanged.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarModelAssignFamilyTest {

    private val dao = CarModelDAO()

    @BeforeAll
    fun setup() = TestDatabaseFactory.start()

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() = TestDatabaseFactory.cleanDatabase()

    private fun seedFamily(brand: String, name: String): UUID = transaction {
        CarFamilyTable.insert {
            it[CarFamilyTable.brand] = brand
            it[CarFamilyTable.name] = name
        }[CarFamilyTable.id].value
    }

    private fun seedModel(brand: String, model: String, familyId: UUID? = null): UUID = transaction {
        CarModelTable.insert {
            it[CarModelTable.brand] = brand
            it[CarModelTable.model] = model
            it[CarModelTable.familyId] = familyId
        }[CarModelTable.id].value
    }

    private fun familyIdOf(modelId: UUID): UUID? = transaction {
        CarModelTable.select(CarModelTable.familyId)
            .where { CarModelTable.id eq modelId }
            .single()[CarModelTable.familyId]
    }

    @Test
    fun `assignToFamily links unassigned models of the matching brand`() = runTest {
        val familyId = seedFamily("volkswagen", "Golf")
        val golfR = seedModel("volkswagen", "golf r")
        val golfVariant = seedModel("volkswagen", "golf variant")

        val result = dao.assignToFamily(familyId, "volkswagen", setOf(golfR, golfVariant))

        assertTrue(result.isSuccess)
        assertEquals(setOf(golfR, golfVariant), result.assignedIds)
        assertEquals(familyId, familyIdOf(golfR))
        assertEquals(familyId, familyIdOf(golfVariant))
    }

    @Test
    fun `assignToFamily re-assigning a model already in the same family is an idempotent no-op`() = runTest {
        val familyId = seedFamily("volkswagen", "Golf")
        val golfR = seedModel("volkswagen", "golf r", familyId = familyId)

        val result = dao.assignToFamily(familyId, "volkswagen", setOf(golfR))

        assertTrue(result.isSuccess)
        assertEquals(setOf(golfR), result.assignedIds)
        assertEquals(familyId, familyIdOf(golfR))
    }

    @Test
    fun `assignToFamily reports missing ids and writes nothing`() = runTest {
        val familyId = seedFamily("volkswagen", "Golf")
        val golfR = seedModel("volkswagen", "golf r")
        val missingId = UUID.randomUUID()

        val result = dao.assignToFamily(familyId, "volkswagen", setOf(golfR, missingId))

        assertFalse(result.isSuccess)
        assertEquals(setOf(missingId), result.missingIds)
        assertTrue(result.assignedIds.isEmpty(), "Nothing should be written when any id is invalid")
        assertNull(familyIdOf(golfR), "The otherwise-valid model must NOT have been assigned")
    }

    @Test
    fun `assignToFamily reports brand mismatches and writes nothing`() = runTest {
        val familyId = seedFamily("volkswagen", "Golf")
        val golfR = seedModel("volkswagen", "golf r")
        val bmwM3 = seedModel("bmw", "m3")

        val result = dao.assignToFamily(familyId, "volkswagen", setOf(golfR, bmwM3))

        assertFalse(result.isSuccess)
        assertEquals(setOf(bmwM3), result.brandMismatchIds)
        assertTrue(result.assignedIds.isEmpty())
        assertNull(familyIdOf(golfR))
        assertNull(familyIdOf(bmwM3))
    }

    @Test
    fun `assignToFamily reports a conflict when a model already belongs to a different family and writes nothing`() = runTest {
        val golfFamily = seedFamily("volkswagen", "Golf")
        val idFamily = seedFamily("volkswagen", "ID")
        val golfR = seedModel("volkswagen", "golf r")
        val idThree = seedModel("volkswagen", "id.3", familyId = idFamily)

        val result = dao.assignToFamily(golfFamily, "volkswagen", setOf(golfR, idThree))

        assertFalse(result.isSuccess)
        assertEquals(setOf(idThree), result.conflictingIds)
        assertTrue(result.assignedIds.isEmpty(), "Nothing should be written, including the clean id")
        assertNull(familyIdOf(golfR))
        assertEquals(idFamily, familyIdOf(idThree), "Must remain linked to its original family")
    }

    @Test
    fun `assignToFamily can report all three failure kinds together`() = runTest {
        val golfFamily = seedFamily("volkswagen", "Golf")
        val idFamily = seedFamily("volkswagen", "ID")
        val golfR = seedModel("volkswagen", "golf r")
        val bmwM3 = seedModel("bmw", "m3")
        val idThree = seedModel("volkswagen", "id.3", familyId = idFamily)
        val missingId = UUID.randomUUID()

        val result = dao.assignToFamily(golfFamily, "volkswagen", setOf(golfR, bmwM3, idThree, missingId))

        assertFalse(result.isSuccess)
        assertEquals(setOf(missingId), result.missingIds)
        assertEquals(setOf(bmwM3), result.brandMismatchIds)
        assertEquals(setOf(idThree), result.conflictingIds)
        assertTrue(result.assignedIds.isEmpty())
        assertNull(familyIdOf(golfR), "The one clean id must not be assigned when the batch fails")
    }
}
