package com.carspotter.core.di

import com.carspotter.features.auth.JwtService
import com.carspotter.core.storage.IStorageService
import com.carspotter.core.storage.LocalImageStorageService
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