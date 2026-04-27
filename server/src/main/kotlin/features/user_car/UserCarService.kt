package com.carspotter.features.user_car

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.user_car.dto.UserCarDTO
import com.carspotter.features.user_car.dto.UserCarRequest
import com.carspotter.features.user_car.dto.UserCarUpdateRequest
import com.carspotter.features.user_car.dto.toDTO
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

interface IUserCarService {
    suspend fun createMyCar(userId: UUID, request: UserCarRequest, imageBytes: ByteArray, contentType: String): UserCarDTO
    suspend fun getMyCar(userId: UUID): UserCarDTO?
    suspend fun getUserCar(userId: UUID): UserCarDTO?
    suspend fun patchMyCar(userId: UUID, request: UserCarUpdateRequest?, imageBytes: ByteArray?, contentType: String?): UserCarDTO
    suspend fun deleteMyCar(userId: UUID)
}

class UserCarServiceImpl(
    private val userCarDao: IUserCarDAO,
    private val storageService: IStorageService,
    private val carModelDao: ICarModelDAO,
) : IUserCarService {
    companion object {
        private val logger = LoggerFactory.getLogger(UserCarServiceImpl::class.java)
        private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        private const val maxBrandLength = 50
        private const val maxModelLength = 80
        private val extensions = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }

    override suspend fun createMyCar(
        userId: UUID,
        request: UserCarRequest,
        imageBytes: ByteArray,
        contentType: String,
    ): UserCarDTO {
        validateImage(imageBytes, contentType)
        val source = validateCreateSource(request.carModelId, request.customBrand, request.customModel)

        if (userCarDao.findByUserId(userId) != null) {
            throw UserCarAlreadyExistsException(userId)
        }

        val imageKey = createImageKey(contentType)
        storageService.uploadImage(imageBytes, imageKey, contentType)

        return try {
            userCarDao.insert(
                userId = userId,
                carModelId = source.carModelId,
                customBrand = source.customBrand,
                customModel = source.customModel,
                imageKey = imageKey,
            )
            requireNotNull(userCarDao.findByUserId(userId)) { "Created user car could not be reloaded" }
                .toResponse()
        } catch (e: Exception) {
            runCatching { storageService.deleteImage(imageKey) }
            if (e is ExposedSQLException) throw UserCarAlreadyExistsException(userId)
            throw UserCarWriteException("Failed to create user car", e)
        }
    }

    override suspend fun getMyCar(userId: UUID): UserCarDTO? =
        userCarDao.findByUserId(userId)?.toResponse()

    override suspend fun getUserCar(userId: UUID): UserCarDTO? =
        userCarDao.findByUserId(userId)?.toResponse()

    override suspend fun patchMyCar(
        userId: UUID,
        request: UserCarUpdateRequest?,
        imageBytes: ByteArray?,
        contentType: String?,
    ): UserCarDTO {
        val existing = userCarDao.findByUserId(userId) ?: throw UserCarNotFoundException(userId)
        if (request == null && imageBytes == null) {
            throw UserCarValidationException("Request must include at least one field to update")
        }

        val source = resolvePatchSource(existing, request)
        var newImageKey: String? = null

        if (imageBytes != null) {
            val safeContentType = contentType ?: throw UserCarValidationException("Missing image content-type")
            validateImage(imageBytes, safeContentType)
            newImageKey = createImageKey(safeContentType)
            storageService.uploadImage(imageBytes, newImageKey, safeContentType)
        }

        return try {
            userCarDao.replaceByUserId(
                userId = userId,
                carModelId = source.carModelId,
                customBrand = source.customBrand,
                customModel = source.customModel,
                imageKey = newImageKey ?: existing.imageKey,
            )
            val updated = userCarDao.findByUserId(userId) ?: throw UserCarNotFoundException(userId)
            if (newImageKey != null) {
                runCatching { storageService.deleteImage(existing.imageKey) }
                    .onFailure { logger.warn("Updated user car image but failed to delete previous image", it) }
            }
            updated.toResponse()
        } catch (e: Exception) {
            if (newImageKey != null) runCatching { storageService.deleteImage(newImageKey) }
            throw UserCarWriteException("Failed to update user car", e)
        }
    }

    override suspend fun deleteMyCar(userId: UUID) {
        val existing = userCarDao.findByUserId(userId) ?: throw UserCarNotFoundException(userId)
        userCarDao.deleteByUserId(userId)
        runCatching { storageService.deleteImage(existing.imageKey) }
            .onFailure { logger.warn("Deleted user car but failed to delete image", it) }
    }

    private suspend fun validateCreateSource(
        carModelId: UUID?,
        customBrand: String?,
        customModel: String?,
    ): CarSource {
        val hasCarModel = carModelId != null
        val brand = customBrand?.let { normalizeBrandOrModel(it, "customBrand", maxBrandLength) }
        val model = customModel?.let { normalizeBrandOrModel(it, "customModel", maxModelLength) }
        val hasCustom = brand != null || model != null

        require(hasCarModel.xor(hasCustom)) {
            "Provide either carModelId or customBrand + customModel"
        }

        return if (hasCarModel) {
            require(brand == null && model == null) {
                "customBrand/customModel cannot be sent together with carModelId"
            }
            require(carModelDao.exists(carModelId!!)) { "carModelId does not exist" }
            CarSource(carModelId = carModelId, customBrand = null, customModel = null)
        } else {
            require(brand != null && model != null) {
                "customBrand and customModel are required when carModelId is missing"
            }
            CarSource(carModelId = null, customBrand = brand, customModel = model)
        }
    }

    private suspend fun resolvePatchSource(existing: UserCar, request: UserCarUpdateRequest?): CarSource {
        if (request == null) {
            return CarSource(existing.carModelId, existing.customBrand, existing.customModel)
        }

        val mentionsCarModel = request.carModelId != null
        val mentionsCustom = request.customBrand != null || request.customModel != null

        if (!mentionsCarModel && !mentionsCustom) {
            return CarSource(existing.carModelId, existing.customBrand, existing.customModel)
        }

        return validateCreateSource(request.carModelId, request.customBrand, request.customModel)
    }

    private fun validateImage(imageBytes: ByteArray, contentType: String) {
        require(imageBytes.isNotEmpty()) { "Image is required" }
        require(contentType in allowedContentTypes) { "Unsupported image content type" }
    }

    private fun normalizeBrandOrModel(value: String, fieldName: String, maxLength: Int): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotBlank()) { "$fieldName cannot be blank" }
        require(normalized.length <= maxLength) { "$fieldName must be at most $maxLength characters" }
        return normalized
    }

    private fun createImageKey(contentType: String): String {
        val ext = extensions.getValue(contentType)
        val today = LocalDate.now()
        return "user-cars/%04d/%02d/%02d/%s.%s".format(
            today.year,
            today.monthValue,
            today.dayOfMonth,
            UUID.randomUUID(),
            ext,
        )
    }

    private fun UserCar.toResponse(): UserCarDTO = toDTO(storageService.resolveUrl(imageKey))
}

private data class CarSource(
    val carModelId: UUID?,
    val customBrand: String?,
    val customModel: String?,
)

class UserCarValidationException(message: String) : RuntimeException(message)

class UserCarAlreadyExistsException(userId: UUID) : RuntimeException("User $userId already has a car")

class UserCarNotFoundException(userId: UUID) : RuntimeException("User $userId does not have a car")

class UserCarWriteException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
