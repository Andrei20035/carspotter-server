package com.revio.server.features.car_family

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class CarFamily(
    val id: UUID,
    val brand: String,
    val name: String,
)

interface ICarFamilyDAO {
    suspend fun create(brand: String, name: String): UUID
    suspend fun findById(id: UUID): CarFamily?
    suspend fun listAll(): List<CarFamily>
    suspend fun exists(id: UUID): Boolean
}

class CarFamilyDAO : ICarFamilyDAO {

    override suspend fun create(brand: String, name: String): UUID = transaction {
        CarFamilyTable.insertReturning(listOf(CarFamilyTable.id)) {
            it[CarFamilyTable.brand] = brand
            it[CarFamilyTable.name] = name
        }.single()[CarFamilyTable.id].value
    }

    override suspend fun findById(id: UUID): CarFamily? = transaction {
        CarFamilyTable
            .select(CarFamilyTable.id, CarFamilyTable.brand, CarFamilyTable.name)
            .where { CarFamilyTable.id eq id }
            .singleOrNull()
            ?.let {
                CarFamily(
                    id = it[CarFamilyTable.id].value,
                    brand = it[CarFamilyTable.brand],
                    name = it[CarFamilyTable.name],
                )
            }
    }

    override suspend fun listAll(): List<CarFamily> = transaction {
        CarFamilyTable
            .select(CarFamilyTable.id, CarFamilyTable.brand, CarFamilyTable.name)
            .orderBy(CarFamilyTable.brand to SortOrder.ASC, CarFamilyTable.name to SortOrder.ASC)
            .map {
                CarFamily(
                    id = it[CarFamilyTable.id].value,
                    brand = it[CarFamilyTable.brand],
                    name = it[CarFamilyTable.name],
                )
            }
    }

    override suspend fun exists(id: UUID): Boolean = transaction {
        CarFamilyTable
            .select(CarFamilyTable.id)
            .where { CarFamilyTable.id eq id }
            .limit(1)
            .any()
    }
}
