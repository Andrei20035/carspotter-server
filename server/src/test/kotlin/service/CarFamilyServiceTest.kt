package service

import com.revio.server.features.car_family.CarFamily
import com.revio.server.features.car_family.CarFamilyAlreadyExistsException
import com.revio.server.features.car_family.CarFamilyNotFoundException
import com.revio.server.features.car_family.CarFamilyService
import com.revio.server.features.car_family.CarModelAlreadyInOtherFamilyException
import com.revio.server.features.car_family.CarModelBrandMismatchException
import com.revio.server.features.car_family.CarModelsNotFoundException
import com.revio.server.features.car_family.ICarFamilyDAO
import com.revio.server.features.car_model.AssignFamilyResult
import com.revio.server.features.car_model.ICarModelDAO
import com.revio.server.features.car_model.dto.CarModelOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * CarFamilyService.assignModels: the service-layer contract on top of
 * ICarModelDAO.assignToFamily (plan §9-E4) — looking the family up for its brand, and mapping
 * the DAO's structured failure buckets to typed exceptions in a fixed priority order.
 */
class CarFamilyServiceTest {

    private val familyId = UUID.randomUUID()
    private val family = CarFamily(id = familyId, brand = "volkswagen", name = "Golf")

    @Test
    fun `assignModels throws CarFamilyNotFoundException for an unknown family`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        coEvery { carFamilyDao.findById(familyId) } returns null

        assertThrows(CarFamilyNotFoundException::class.java) {
            runBlocking {
                CarFamilyService(carFamilyDao, mockk(relaxed = true)).assignModels(familyId, listOf(UUID.randomUUID()))
            }
        }
    }

    @Test
    fun `assignModels rejects an empty id list`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        coEvery { carFamilyDao.findById(familyId) } returns family

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                CarFamilyService(carFamilyDao, mockk(relaxed = true)).assignModels(familyId, emptyList())
            }
        }
    }

    @Test
    fun `assignModels returns the family's models on success`() = runTest {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>()
        val modelId = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family
        coEvery { carModelDao.assignToFamily(familyId, "volkswagen", setOf(modelId)) } returns
            AssignFamilyResult(assignedIds = setOf(modelId), missingIds = emptySet(), brandMismatchIds = emptySet(), conflictingIds = emptySet())
        coEvery { carModelDao.getModelsForFamily(familyId) } returns listOf(CarModelOption(modelId, "golf r"))

        val result = CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(modelId))

        assertEquals(listOf(CarModelOption(modelId, "golf r")), result)
    }

    @Test
    fun `assignModels throws CarModelsNotFoundException when the DAO reports missing ids`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>()
        val missing = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family
        coEvery { carModelDao.assignToFamily(familyId, "volkswagen", setOf(missing)) } returns
            AssignFamilyResult(assignedIds = emptySet(), missingIds = setOf(missing), brandMismatchIds = emptySet(), conflictingIds = emptySet())

        val exception = assertThrows(CarModelsNotFoundException::class.java) {
            runBlocking { CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(missing)) }
        }
        assertEquals(setOf(missing), exception.missingIds)
    }

    @Test
    fun `assignModels throws CarModelBrandMismatchException when the DAO reports a brand mismatch`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>()
        val bmwM3 = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family
        coEvery { carModelDao.assignToFamily(familyId, "volkswagen", setOf(bmwM3)) } returns
            AssignFamilyResult(assignedIds = emptySet(), missingIds = emptySet(), brandMismatchIds = setOf(bmwM3), conflictingIds = emptySet())

        val exception = assertThrows(CarModelBrandMismatchException::class.java) {
            runBlocking { CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(bmwM3)) }
        }
        assertEquals(setOf(bmwM3), exception.mismatchedIds)
        assertEquals("volkswagen", exception.expectedBrand)
    }

    @Test
    fun `assignModels throws CarModelAlreadyInOtherFamilyException when the DAO reports a conflict`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>()
        val idThree = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family
        coEvery { carModelDao.assignToFamily(familyId, "volkswagen", setOf(idThree)) } returns
            AssignFamilyResult(assignedIds = emptySet(), missingIds = emptySet(), brandMismatchIds = emptySet(), conflictingIds = setOf(idThree))

        val exception = assertThrows(CarModelAlreadyInOtherFamilyException::class.java) {
            runBlocking { CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(idThree)) }
        }
        assertEquals(setOf(idThree), exception.conflictingIds)
    }

    @Test
    fun `assignModels prioritizes missing ids over brand mismatch and conflict when a request has more than one problem`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>()
        val missing = UUID.randomUUID()
        val bmwM3 = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family
        coEvery { carModelDao.assignToFamily(familyId, "volkswagen", setOf(missing, bmwM3)) } returns
            AssignFamilyResult(assignedIds = emptySet(), missingIds = setOf(missing), brandMismatchIds = setOf(bmwM3), conflictingIds = emptySet())

        assertThrows(CarModelsNotFoundException::class.java) {
            runBlocking { CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(missing, bmwM3)) }
        }
    }

    @Test
    fun `assignModels passes the family's own brand to the DAO, not caller input`() = runTest {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        val carModelDao = mockk<ICarModelDAO>(relaxed = true)
        val modelId = UUID.randomUUID()
        coEvery { carFamilyDao.findById(familyId) } returns family

        CarFamilyService(carFamilyDao, carModelDao).assignModels(familyId, listOf(modelId))

        coVerify(exactly = 1) { carModelDao.assignToFamily(familyId, "volkswagen", setOf(modelId)) }
    }

    // ---------- createFamily — unchanged sanity checks kept alongside the new tests ----------

    @Test
    fun `createFamily still rejects a duplicate brand-name pair`() {
        val carFamilyDao = mockk<ICarFamilyDAO>()
        coEvery { carFamilyDao.create("volkswagen", "Golf") } throws
            org.jetbrains.exposed.exceptions.ExposedSQLException(
                java.sql.SQLException("dup", "23505"), emptyList(), mockk(relaxed = true),
            )

        assertThrows(CarFamilyAlreadyExistsException::class.java) {
            runBlocking {
                CarFamilyService(carFamilyDao, mockk(relaxed = true)).createFamily("volkswagen", "Golf")
            }
        }
    }
}
