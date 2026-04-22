package com.carspotter.features.user_car

import com.carspotter.features.user.User
import java.util.*

interface IUserCarRepository {
    suspend fun createUserCar(userCar: UserCar): UUID
    suspend fun getUserCarById(userCarId: UUID): UserCar?
    suspend fun getUserCarByUserId(userId: UUID): UserCar?
    suspend fun getUserByUserCarId(userCarId: UUID): User
    suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int
    suspend fun deleteUserCar(userId: UUID): Int
    suspend fun getAllUserCars(): List<UserCar>
}

class UserCarRepository(
    private val userCarDao: IUserCarDAO,
) : IUserCarRepository {
    override suspend fun createUserCar(userCar: UserCar): UUID {
        return userCarDao.createUserCar(userCar)
    }

    override suspend fun getUserCarById(userCarId: UUID): UserCar? {
        return userCarDao.getUserCarById(userCarId)
    }

    override suspend fun getUserCarByUserId(userId: UUID): UserCar? {
        return userCarDao.getUserCarByUserId(userId)
    }

    override suspend fun getUserByUserCarId(userCarId: UUID): User {
        return userCarDao.getUserByUserCarId(userCarId)
    }

    override suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int {
        return userCarDao.updateUserCar(userId, imagePath, carModelId)
    }

    override suspend fun deleteUserCar(userId: UUID): Int {
        return userCarDao.deleteUserCar(userId)
    }

    override suspend fun getAllUserCars(): List<UserCar> {
        return userCarDao.getAllUserCars()
    }
}