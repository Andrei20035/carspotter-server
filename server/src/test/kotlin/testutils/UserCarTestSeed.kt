package testutils

import com.carspotter.features.user_car.UserCarTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object UserCarTestSeed {
    fun insertUserCar(
        userId: UUID,
        carModelId: UUID? = null,
        customBrand: String? = "BMW",
        customModel: String? = "M3",
        imageKey: String = "user-cars/test.jpg",
    ): UUID = transaction {
        UserCarTable.insert {
            it[UserCarTable.userId] = userId
            it[UserCarTable.carModelId] = carModelId
            it[UserCarTable.customBrand] = customBrand
            it[UserCarTable.customModel] = customModel
            it[UserCarTable.imageKey] = imageKey
        }[UserCarTable.id].value
    }
}
