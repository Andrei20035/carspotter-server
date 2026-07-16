package com.revio.server.core.di

import com.revio.server.features.auth.JwtService
import com.revio.server.core.storage.IStorageService
import com.revio.server.core.storage.LocalImageStorageService
import org.koin.dsl.module
import java.nio.file.Files
import java.nio.file.Paths

val appModule = module {
    single<IStorageService> {
        val baseDir = Paths.get(System.getenv("LOCAL_STORAGE_BASE_DIR") ?: "uploads")
        val publicBaseUrl = System.getenv("PUBLIC_BASE_URL") ?: "http://localhost:8080"
        Files.createDirectories(baseDir)
        LocalImageStorageService(baseDir = baseDir, publicBaseUrl = publicBaseUrl)
    }
}