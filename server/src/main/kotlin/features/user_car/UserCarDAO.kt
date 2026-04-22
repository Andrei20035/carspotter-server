package com.carspotter.features.user_car

import com.carspotter.features.user.User
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*

interface IUserCarDAO {
    suspend fun createUserCar(userCar: UserCar): UUID
    suspend fun getUserCarById(userCarId: UUID): UserCar?
    suspend fun getUserCarByUserId(userId: UUID): UserCar?
    suspend fun getUserByUserCarId(userCarId: UUID): User
    suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int
    suspend fun deleteUserCar(userId: UUID): Int
    suspend fun getAllUserCars(): List<UserCar>
}

class UserCarDAO : IUserCarDAO {
    override suspend fun createUserCar(userCar: UserCar): UUID {
        return transaction {
            UserCarTable.insertReturning(listOf(UserCarTable.id)) {
                it[userId] = userCar.userId
                it[carModelId] = userCar.carModelId
                it[imagePath] = userCar.imagePath
            }.singleOrNull()?.get(UserCarTable.id)?.value ?: throw IllegalStateException("Failed to create user car")
        }
    }

    override suspend fun getUserCarById(userCarId: UUID): UserCar? {
        return transaction {
            UserCarTable
                .selectAll()
                .where { UserCarTable.id eq userCarId }
                .mapNotNull { row ->
                    UserCar(
                        id = row[UserCarTable.id].value,
                        userId = row[UserCarTable.userId],
                        carModelId = row[UserCarTable.carModelId],
                        imagePath = row[UserCarTable.imagePath],
                        createdAt = row[UserCarTable.createdAt],
                        updatedAt = row[UserCarTable.updatedAt]
                    )
                }.singleOrNull()
        }
    }

    override suspend fun getUserCarByUserId(userId: UUID): UserCar? {
        return transaction {
            UserCarTable
                .selectAll()
                .where { UserCarTable.userId eq userId }
                .mapNotNull { row ->
                    UserCar(
                        id = row[UserCarTable.id].value,
                        userId = row[UserCarTable.userId],
                        carModelId = row[UserCarTable.carModelId],
                        imagePath = row[UserCarTable.imagePath],
                        createdAt = row[UserCarTable.createdAt],
                        updatedAt = row[UserCarTable.updatedAt]
                    )
                }.singleOrNull()
        }
    }

    override suspend fun getUserByUserCarId(userCarId: UUID): User {
        return transaction {
            (UserCarTable innerJoin UserTable)
                .selectAll()
                .where { UserCarTable.id eq userCarId }
                .mapNotNull { row ->
                    User(
                        id = row[UserTable.id].value,
                        authCredentialId = row[UserTable.authCredentialId],
                        profilePicturePath = row[UserTable.profilePicturePath],
                        fullName = row[UserTable.fullName],
                        phoneNumber = row[UserTable.phoneNumber],
                        birthDate = row[UserTable.birthDate],
                        username = row[UserTable.username],
                        country = row[UserTable.country],
                        spotScore = row[UserTable.spotScore],
                        createdAt = row[UserTable.createdAt],
                        updatedAt = row[UserTable.updatedAt]
                    )
                }.singleOrNull() ?: throw NoSuchElementException("User with userCarId: $userCarId not found")
        }
    }

    override suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int {
        return transaction {
            UserCarTable.update({ UserCarTable.userId eq userId }) { row ->
                if (imagePath != null) row[UserCarTable.imagePath] = imagePath
                if (carModelId != null) row[UserCarTable.carModelId] = carModelId
            }
        }
    }

    override suspend fun deleteUserCar(userId: UUID): Int {
        return transaction {
            UserCarTable.deleteWhere { UserCarTable.userId eq userId }
        }
    }

    override suspend fun getAllUserCars(): List<UserCar> {
        return transaction {
            UserCarTable
                .selectAll()
                .mapNotNull { row ->
                    UserCar(
                        userId = row[UserCarTable.userId],
                        carModelId = row[UserCarTable.carModelId],
                        imagePath = row[UserCarTable.imagePath],
                    )
                }
        }
    }

}