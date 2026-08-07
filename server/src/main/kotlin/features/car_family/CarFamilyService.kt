package com.revio.server.features.car_family

import com.revio.server.features.car_model.ICarModelDAO
import com.revio.server.features.car_model.dto.CarModelOption
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.UUID

/** Postgres SQLSTATE for a unique constraint violation. */
private const val POSTGRES_UNIQUE_VIOLATION = "23505"

class CarFamilyNotFoundException(familyId: UUID) : RuntimeException("Car family $familyId not found")

class CarFamilyAlreadyExistsException(brand: String, name: String) :
    RuntimeException("Car family '$brand $name' already exists")

/** One or more requested car model ids don't exist. */
class CarModelsNotFoundException(val missingIds: Set<UUID>) :
    RuntimeException("Car models not found: $missingIds")

/** One or more requested car models belong to a different brand than the target family. */
class CarModelBrandMismatchException(val mismatchedIds: Set<UUID>, val expectedBrand: String) :
    RuntimeException("Car models do not belong to brand '$expectedBrand': $mismatchedIds")

/** One or more requested car models are already linked to a different family. */
class CarModelAlreadyInOtherFamilyException(val conflictingIds: Set<UUID>) :
    RuntimeException("Car models are already assigned to a different family: $conflictingIds")

interface ICarFamilyService {
    /** Creates a family, e.g. (brand = "Volkswagen", name = "Golf"). Rejects a duplicate (brand, name) pair. */
    suspend fun createFamily(brand: String, name: String): CarFamily

    suspend fun getFamily(familyId: UUID): CarFamily?

    /** Batch counterpart to [getFamily] — see [ICarFamilyDAO.findByIds]. */
    suspend fun getFamilies(familyIds: Set<UUID>): Map<UUID, CarFamily>

    suspend fun listFamilies(): List<CarFamily>

    /** Every car_models row currently linked to [familyId]. */
    suspend fun getModelsForFamily(familyId: UUID): List<CarModelOption>

    /** car_models rows for [brand] not yet linked to any family — helps an admin build a new one. */
    suspend fun getUnassignedModelsForBrand(brand: String): List<CarModelOption>

    /**
     * Assigns [carModelIds] to [familyId], atomically (all-or-nothing — see
     * [ICarModelDAO.assignToFamily]). A model already in [familyId] is a no-op, which makes this
     * safely retriable. Throws [CarFamilyNotFoundException], [CarModelsNotFoundException],
     * [CarModelBrandMismatchException], or [CarModelAlreadyInOtherFamilyException] — in that
     * priority order when a request has more than one kind of problem — and in every failure case
     * nothing is written. On success, returns every model now linked to the family.
     */
    suspend fun assignModels(familyId: UUID, carModelIds: List<UUID>): List<CarModelOption>
}

class CarFamilyService(
    private val carFamilyDao: ICarFamilyDAO,
    private val carModelDao: ICarModelDAO,
) : ICarFamilyService {

    override suspend fun createFamily(brand: String, name: String): CarFamily {
        val trimmedBrand = brand.trim()
        val trimmedName = name.trim()
        require(trimmedBrand.isNotEmpty()) { "brand must not be blank" }
        require(trimmedName.isNotEmpty()) { "name must not be blank" }

        val id = try {
            carFamilyDao.create(trimmedBrand, trimmedName)
        } catch (e: ExposedSQLException) {
            if (e.sqlState == POSTGRES_UNIQUE_VIOLATION) throw CarFamilyAlreadyExistsException(trimmedBrand, trimmedName)
            throw e
        }

        return carFamilyDao.findById(id) ?: error("Failed to load car family $id right after insert")
    }

    override suspend fun getFamily(familyId: UUID): CarFamily? = carFamilyDao.findById(familyId)

    override suspend fun getFamilies(familyIds: Set<UUID>): Map<UUID, CarFamily> = carFamilyDao.findByIds(familyIds)

    override suspend fun listFamilies(): List<CarFamily> = carFamilyDao.listAll()

    override suspend fun getModelsForFamily(familyId: UUID): List<CarModelOption> {
        if (!carFamilyDao.exists(familyId)) throw CarFamilyNotFoundException(familyId)
        return carModelDao.getModelsForFamily(familyId)
    }

    override suspend fun getUnassignedModelsForBrand(brand: String): List<CarModelOption> =
        carModelDao.getUnassignedModelsForBrand(brand.trim().lowercase())

    override suspend fun assignModels(familyId: UUID, carModelIds: List<UUID>): List<CarModelOption> {
        val family = carFamilyDao.findById(familyId) ?: throw CarFamilyNotFoundException(familyId)
        require(carModelIds.isNotEmpty()) { "carModelIds must not be empty" }

        val result = carModelDao.assignToFamily(familyId, family.brand, carModelIds.toSet())
        when {
            result.missingIds.isNotEmpty() -> throw CarModelsNotFoundException(result.missingIds)
            result.brandMismatchIds.isNotEmpty() -> throw CarModelBrandMismatchException(result.brandMismatchIds, family.brand)
            result.conflictingIds.isNotEmpty() -> throw CarModelAlreadyInOtherFamilyException(result.conflictingIds)
        }

        return carModelDao.getModelsForFamily(familyId)
    }
}
