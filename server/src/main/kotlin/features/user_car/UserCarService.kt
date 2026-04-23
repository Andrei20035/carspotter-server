package com.carspotter.features.user_car

import com.carspotter.features.user_car.dto.UserCarDTO
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.user.dto.toDTO
import com.carspotter.features.user_car.dto.toDTO
import java.util.*

interface IUserCarService {
    suspend fun createUserCar(userCar: UserCar): UUID
    suspend fun getUserCarById(userCarId: UUID): UserCarDTO?
    suspend fun getUserCarByUserId(userId: UUID): UserCarDTO?
    suspend fun getUserByUserCarId(userCarId: UUID): UserDTO
    suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int
    suspend fun deleteUserCar(userId: UUID): Int
    suspend fun getAllUserCars(): List<UserCarDTO>
}

class UserCarServiceImpl(
    private val userCarRepository: IUserCarRepository
): IUserCarService {
    override suspend fun createUserCar(userCar: UserCar): UUID {
        return try {
            userCarRepository.createUserCar(userCar)
        } catch (e: IllegalStateException) {
            throw UserCarCreationException("Invalid userId or carModelId", e)
        }
    }

    override suspend fun getUserCarById(userCarId: UUID): UserCarDTO? {
        return userCarRepository.getUserCarById(userCarId)?.toDTO()
    }

    override suspend fun getUserCarByUserId(userId: UUID): UserCarDTO? {
        return userCarRepository.getUserCarByUserId(userId)?.toDTO()
    }

    override suspend fun getUserByUserCarId(userCarId: UUID): UserDTO {
        return userCarRepository.getUserByUserCarId(userCarId).toDTO()
    }

    override suspend fun updateUserCar(userId: UUID, imagePath: String?, carModelId: UUID?): Int {
        return userCarRepository.updateUserCar(userId, imagePath, carModelId)
    }

    override suspend fun deleteUserCar(userId: UUID): Int {
        return userCarRepository.deleteUserCar(userId)
    }

    override suspend fun getAllUserCars(): List<UserCarDTO> {
        return userCarRepository.getAllUserCars().map { it.toDTO() }
    }
}

class UserCarCreationException(message: String, cause: Throwable? = null): Exception(message, cause)