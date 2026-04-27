package service

import com.carspotter.features.car_model.CarModelService
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.car_model.dto.CarModelOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class CarModelServiceTest {

    @Test
    fun `getCarModelsForBrand normalizes brand by lowercasing`() = runTest {
        val dao = mockk<ICarModelDAO>()
        val capturedBrand = slot<String>()
        coEvery { dao.getCarModelsForBrand(capture(capturedBrand)) } returns emptyList()

        val service = CarModelService(dao)
        service.getCarModelsForBrand("BMW")

        assertEquals("bmw", capturedBrand.captured)
        coVerify(exactly = 1) { dao.getCarModelsForBrand("bmw") }
    }

    @Test
    fun `getCarModelsForBrand normalizes brand by trimming whitespace`() = runTest {
        val dao = mockk<ICarModelDAO>()
        val capturedBrand = slot<String>()
        coEvery { dao.getCarModelsForBrand(capture(capturedBrand)) } returns emptyList()

        val service = CarModelService(dao)
        service.getCarModelsForBrand("  BMW  ")

        assertEquals("bmw", capturedBrand.captured)
    }

    @Test
    fun `getCarModelsForBrand normalizes multi-word brand`() = runTest {
        val dao = mockk<ICarModelDAO>()
        val capturedBrand = slot<String>()
        coEvery { dao.getCarModelsForBrand(capture(capturedBrand)) } returns emptyList()

        val service = CarModelService(dao)
        service.getCarModelsForBrand("Alfa Romeo")

        assertEquals("alfa romeo", capturedBrand.captured)
    }

    @Test
    fun `getCarModelsForBrand returns DAO result unchanged`() = runTest {
        val dao = mockk<ICarModelDAO>()
        val expected = listOf(
            CarModelOption(id = UUID.randomUUID(), model = "m3"),
            CarModelOption(id = UUID.randomUUID(), model = "m4"),
        )
        coEvery { dao.getCarModelsForBrand("bmw") } returns expected

        val service = CarModelService(dao)
        val result = service.getCarModelsForBrand("BMW")

        assertEquals(expected, result)
    }

    @Test
    fun `getAllCarBrands is passthrough returning DAO result unchanged`() = runTest {
        val dao = mockk<ICarModelDAO>()
        val expected = listOf("alfa romeo", "audi", "bmw")
        coEvery { dao.getAllCarBrands() } returns expected

        val service = CarModelService(dao)
        val result = service.getAllCarBrands()

        assertEquals(expected, result)
        coVerify(exactly = 1) { dao.getAllCarBrands() }
    }
}