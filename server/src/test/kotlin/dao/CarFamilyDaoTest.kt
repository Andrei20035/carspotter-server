package dao

import com.revio.server.features.car_family.CarFamilyDAO
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import java.util.UUID

/** ICarFamilyDAO: plain CRUD/lookup backing CarFamilyService, no branching logic of its own. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarFamilyDaoTest {

    private val dao = CarFamilyDAO()

    @BeforeAll
    fun setup() = TestDatabaseFactory.start()

    @AfterAll
    fun tearDown() = TestDatabaseFactory.stop()

    @BeforeEach
    fun clean() = TestDatabaseFactory.cleanDatabase()

    @Test
    fun `create returns an id and findById returns the same brand and name`() = runTest {
        val id = dao.create("volkswagen", "Golf")

        val found = dao.findById(id)

        assertEquals(id, found?.id)
        assertEquals("volkswagen", found?.brand)
        assertEquals("Golf", found?.name)
    }

    @Test
    fun `create rejects a duplicate (brand, name) pair with a unique violation`() = runTest {
        dao.create("volkswagen", "Golf")

        val exception = assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking { dao.create("volkswagen", "Golf") }
        }
        assertEquals("23505", exception.sqlState)
    }

    @Test
    fun `create allows the same name under a different brand`() = runTest {
        val vwGolf = dao.create("volkswagen", "Golf")
        val audiA1 = dao.create("audi", "Golf") // same name, different brand — not a real model, but the constraint is scoped per-brand

        assertTrue(vwGolf != audiA1)
        assertEquals("audi", dao.findById(audiA1)?.brand)
    }

    @Test
    fun `findById returns null for an unknown id`() = runTest {
        assertNull(dao.findById(UUID.randomUUID()))
    }

    @Test
    fun `listAll returns every family ordered by brand then name`() = runTest {
        dao.create("volkswagen", "Golf")
        dao.create("audi", "A4 family")
        dao.create("volkswagen", "Arteon")

        val families = dao.listAll()

        assertEquals(
            listOf("audi" to "A4 family", "volkswagen" to "Arteon", "volkswagen" to "Golf"),
            families.map { it.brand to it.name },
        )
    }

    @Test
    fun `listAll returns an empty list when no families exist`() = runTest {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `exists is true for a created family and false for a random id`() = runTest {
        val id = dao.create("volkswagen", "Golf")

        assertTrue(dao.exists(id))
        assertFalse(dao.exists(UUID.randomUUID()))
    }
}
