package com.revio.server.features.car_model

import com.revio.server.features.car_model.dto.CarModelOption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Outcome of [ICarModelDAO.assignToFamily]. All-or-nothing: [assignedIds] is non-empty (the
 * models were actually written) only when [missingIds], [brandMismatchIds] and [conflictingIds]
 * are all empty — see the method's KDoc for why nothing is written on partial failure.
 */
data class AssignFamilyResult(
    val assignedIds: Set<java.util.UUID>,
    val missingIds: Set<java.util.UUID>,
    val brandMismatchIds: Set<java.util.UUID>,
    val conflictingIds: Set<java.util.UUID>,
) {
    val isSuccess: Boolean get() = missingIds.isEmpty() && brandMismatchIds.isEmpty() && conflictingIds.isEmpty()
}

interface ICarModelDAO {
    suspend fun getAllCarBrands(): List<String>
    suspend fun getCarModelsForBrand(brand: String): List<CarModelOption>
    suspend fun exists(carModelId: java.util.UUID): Boolean

    /** All car_models rows currently linked to [familyId], e.g. every Golf variant. */
    suspend fun getModelsForFamily(familyId: java.util.UUID): List<CarModelOption>

    /** car_models rows for [brand] not yet linked to any family — helps an admin build a new one. */
    suspend fun getUnassignedModelsForBrand(brand: String): List<CarModelOption>

    /**
     * Assigns [carModelIds] to [familyId] (whose brand is [familyBrand] — the caller already
     * looked the family up to confirm it exists) in ONE transaction, all-or-nothing.
     *
     * Three ways a request can be rejected, all reported together rather than one at a time:
     * an id that isn't a real car_models row ([AssignFamilyResult.missingIds]), an id whose brand
     * doesn't match [familyBrand] ([AssignFamilyResult.brandMismatchIds] — the DB's composite FK
     * would reject this too, but a service-level check gives a clear message instead of a raw SQL
     * error), or an id already linked to a *different* family ([AssignFamilyResult.conflictingIds]
     * — reassigning away from another family is a decision this endpoint deliberately doesn't
     * make silently). If any of the three is non-empty, NOTHING is written — not even the ids that
     * would otherwise be valid — so a caller can fix the request and resubmit it as a whole.
     *
     * A model already linked to [familyId] itself is treated as a no-op success (included in
     * [AssignFamilyResult.assignedIds]): this makes the endpoint safely retriable.
     */
    suspend fun assignToFamily(familyId: java.util.UUID, familyBrand: String, carModelIds: Set<java.util.UUID>): AssignFamilyResult
}

class CarModelDAO : ICarModelDAO {
    override suspend fun getAllCarBrands(): List<String> = transaction {
        CarModelTable
            .select(CarModelTable.brand)
            .withDistinct()
            .orderBy(CarModelTable.brand to SortOrder.ASC)
            .map { it[CarModelTable.brand] }
    }

    override suspend fun getCarModelsForBrand(brand: String): List<CarModelOption> = transaction {
        CarModelTable
            .select(CarModelTable.id, CarModelTable.model)
            .where { CarModelTable.brand.lowerCase() eq brand }
            .orderBy(CarModelTable.model to SortOrder.ASC)
            .map {
                CarModelOption(
                    id = it[CarModelTable.id].value,
                    model = it[CarModelTable.model],
                )
            }
    }

    override suspend fun exists(carModelId: java.util.UUID): Boolean = transaction {
        CarModelTable
            .select(CarModelTable.id)
            .where { CarModelTable.id eq carModelId }
            .limit(1)
            .any()
    }

    override suspend fun getModelsForFamily(familyId: java.util.UUID): List<CarModelOption> = transaction {
        CarModelTable
            .select(CarModelTable.id, CarModelTable.model)
            .where { CarModelTable.familyId eq familyId }
            .orderBy(CarModelTable.model to SortOrder.ASC)
            .map {
                CarModelOption(
                    id = it[CarModelTable.id].value,
                    model = it[CarModelTable.model],
                )
            }
    }

    override suspend fun getUnassignedModelsForBrand(brand: String): List<CarModelOption> = transaction {
        CarModelTable
            .select(CarModelTable.id, CarModelTable.model)
            .where { (CarModelTable.brand.lowerCase() eq brand) and (CarModelTable.familyId.isNull()) }
            .orderBy(CarModelTable.model to SortOrder.ASC)
            .map {
                CarModelOption(
                    id = it[CarModelTable.id].value,
                    model = it[CarModelTable.model],
                )
            }
    }

    override suspend fun assignToFamily(
        familyId: java.util.UUID,
        familyBrand: String,
        carModelIds: Set<java.util.UUID>,
    ): AssignFamilyResult = transaction {
        val rows = CarModelTable
            .select(CarModelTable.id, CarModelTable.brand, CarModelTable.familyId)
            .where { CarModelTable.id inList carModelIds }
            .associateBy { it[CarModelTable.id].value }

        val missingIds = carModelIds - rows.keys
        val existing = rows.filterKeys { it !in missingIds }

        val brandMismatchIds = existing.filterValues { it[CarModelTable.brand] != familyBrand }.keys
        val brandOk = existing - brandMismatchIds

        val conflictingIds = brandOk.filterValues { row ->
            val currentFamilyId = row[CarModelTable.familyId]
            currentFamilyId != null && currentFamilyId != familyId
        }.keys

        val requestIsClean = missingIds.isEmpty() && brandMismatchIds.isEmpty() && conflictingIds.isEmpty()
        val toAssign = if (requestIsClean) brandOk.keys - conflictingIds else emptySet()

        if (toAssign.isNotEmpty()) {
            CarModelTable.update({ CarModelTable.id inList toAssign }) {
                it[CarModelTable.familyId] = familyId
            }
        }

        AssignFamilyResult(
            assignedIds = toAssign,
            missingIds = missingIds,
            brandMismatchIds = brandMismatchIds,
            conflictingIds = conflictingIds,
        )
    }
}
