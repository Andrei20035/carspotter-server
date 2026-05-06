package com.carspotter.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
    val ktorEnv = System.getenv("KTOR_ENV") ?: "development"

    val (databaseUrl, dbUser, dbPassword) = when (ktorEnv) {
        "development" -> Triple(
            System.getenv("DEV_DB_URL") ?: error("DEV_DB_URL not set"),
            System.getenv("DEV_USER") ?: error("DEV_USER not set"),
            System.getenv("DEV_PASSWORD") ?: error("DEV_PASSWORD not set")
        )
        "testing" -> Triple(
            System.getenv("TEST_DB_URL") ?: error("TEST_DB_URL not set"),
            System.getenv("TEST_DB_USER") ?: error("TEST_DB_USER not set"),
            System.getenv("TEST_DB_PASSWORD") ?: error("TEST_DB_PASSWORD not set")
        )
        "production" -> Triple(
            System.getenv("PROD_DB_URL") ?: error("PROD_DB_URL not set"),
            System.getenv("PROD_USER") ?: error("PROD_USER not set"),
            System.getenv("PROD_PASSWORD") ?: error("PROD_PASSWORD not set")
        )
        else -> error("Unknown KTOR_ENV: $ktorEnv")
    }

    val jdbcUrlFinal = if (databaseUrl.startsWith("jdbc:")) {
        databaseUrl
    } else {
        databaseUrl.replaceFirst("postgresql://", "jdbc:postgresql://")
    }

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = jdbcUrlFinal
        driverClassName = "org.postgresql.Driver"
        username = dbUser
        password = dbPassword
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    val dataSource = HikariDataSource(hikariConfig)

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migrations")
        .load()
        .migrate()

    Database.connect(dataSource)

    environment.log.info("Database connected and migrations applied using Flyway for env=$ktorEnv")

}