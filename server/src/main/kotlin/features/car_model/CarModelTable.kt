package com.revio.server.features.car_model

import org.jetbrains.exposed.dao.id.UUIDTable

object CarModelTable : UUIDTable("car_models") {
    val brand = varchar("brand", 50)
    val model = varchar("model", 50)

    // Nullable link to a car_families row. Only a plain column here: the DB enforces a composite
    // FK (brand, family_id) -> car_families(brand, id) so a model can only join a family of its
    // own brand, which Exposed's single-column .references() can't express — see V22 migration.
    val familyId = uuid("family_id").nullable()

    init {
        uniqueIndex(brand, model)
    }

}