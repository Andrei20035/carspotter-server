package com.revio.server.features.car_family

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Groups car_models rows under a stable target (e.g. all Golf variants under
 * "Volkswagen Golf") so a challenge can reference one family instead of every
 * individual model. Scoped to a brand: car_models.family_id carries a composite
 * FK (brand, family_id) enforcing that a model can only join a family of its own brand.
 */
object CarFamilyTable : UUIDTable("car_families") {
    val brand = varchar("brand", 50)
    val name = varchar("name", 80)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(brand, name)
    }
}
