package testutils

import com.carspotter.features.car_model.CarModelTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Seed standardizat pentru testele de car-models.
 * Datele sunt deja normalizate (lowercase + trimmed), așa cum impune convenția proiectului.
 */
object CarModelTestSeed {

    data class Seeded(
        val bmwM3: UUID,
        val bmwM4: UUID,
        val audiRs6: UUID,
        val alfaGiulia: UUID,
    )

    fun seedDefault(): Seeded = transaction {
        val bmwM3 = insertModel("bmw", "m3")
        val bmwM4 = insertModel("bmw", "m4")
        val audiRs6 = insertModel("audi", "rs6")
        val alfaGiulia = insertModel("alfa romeo", "giulia")
        Seeded(bmwM3, bmwM4, audiRs6, alfaGiulia)
    }

    fun insertModel(brand: String, model: String): UUID = transaction {
        CarModelTable.insert {
            it[CarModelTable.brand] = brand
            it[CarModelTable.model] = model
        }[CarModelTable.id].value
    }
}