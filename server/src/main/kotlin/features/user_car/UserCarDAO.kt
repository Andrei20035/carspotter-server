package com.carspotter.features.user_car

import com.carspotter.features.car_model.CarModelTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

interface IUserCarDAO {
    suspend fun insert(
        userId: UUID,
        carModelId: UUID?,
        customBrand: String?,
        customModel: String?,
        imageKey: String,
    ): UUID

    suspend fun findByUserId(userId: UUID): UserCar?

    suspend fun replaceByUserId(
        userId: UUID,
        carModelId: UUID?,
        customBrand: String?,
        customModel: String?,
        imageKey: String,
    ): Int

    suspend fun deleteByUserId(userId: UUID): Int
}

class UserCarDAO : IUserCarDAO {
    private val selectedColumns = listOf(
        UserCarTable.id,
        UserCarTable.userId,
        UserCarTable.carModelId,
        UserCarTable.customBrand,
        UserCarTable.customModel,
        UserCarTable.imageKey,
        UserCarTable.createdAt,
        UserCarTable.updatedAt,
        CarModelTable.brand,
        CarModelTable.model,
    )

    private fun baseQuery() = UserCarTable
        .join(CarModelTable, JoinType.LEFT, additionalConstraint = { UserCarTable.carModelId eq CarModelTable.id })
        .select(selectedColumns)

    override suspend fun insert(
        userId: UUID,
        carModelId: UUID?,
        customBrand: String?,
        customModel: String?,
        imageKey: String,
    ): UUID = transaction {
        UserCarTable.insertReturning(listOf(UserCarTable.id)) {
            it[UserCarTable.userId] = userId
            it[UserCarTable.carModelId] = carModelId
            it[UserCarTable.customBrand] = customBrand
            it[UserCarTable.customModel] = customModel
            it[UserCarTable.imageKey] = imageKey
        }.singleOrNull()?.get(UserCarTable.id)?.value
            ?: error("Failed to create user car")
    }

    override suspend fun findByUserId(userId: UUID): UserCar? = transaction {
        baseQuery()
            .where { UserCarTable.userId eq userId }
            .singleOrNull()
            ?.toUserCar()
    }

    override suspend fun replaceByUserId(
        userId: UUID,
        carModelId: UUID?,
        customBrand: String?,
        customModel: String?,
        imageKey: String,
    ): Int = transaction {
        UserCarTable.update({ UserCarTable.userId eq userId }) { row ->
            row[UserCarTable.carModelId] = carModelId
            row[UserCarTable.customBrand] = customBrand
            row[UserCarTable.customModel] = customModel
            row[UserCarTable.imageKey] = imageKey
        }
    }

    override suspend fun deleteByUserId(userId: UUID): Int = transaction {
        UserCarTable.deleteWhere { UserCarTable.userId eq userId }
    }
}

private fun ResultRow.toUserCar(): UserCar {
    val resolvedBrand = if (this[UserCarTable.carModelId] != null) this[CarModelTable.brand] else this[UserCarTable.customBrand]
    val resolvedModel = if (this[UserCarTable.carModelId] != null) this[CarModelTable.model] else this[UserCarTable.customModel]

    return UserCar(
        id = this[UserCarTable.id].value,
        userId = this[UserCarTable.userId],
        carModelId = this[UserCarTable.carModelId],
        customBrand = this[UserCarTable.customBrand],
        customModel = this[UserCarTable.customModel],
        brand = resolvedBrand ?: error("User car ${this[UserCarTable.id].value} is missing brand"),
        model = resolvedModel ?: error("User car ${this[UserCarTable.id].value} is missing model"),
        imageKey = this[UserCarTable.imageKey],
        createdAt = this[UserCarTable.createdAt],
        updatedAt = this[UserCarTable.updatedAt],
    )
}
