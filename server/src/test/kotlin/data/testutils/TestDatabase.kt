package data.testutils

import org.testcontainers.containers.PostgreSQLContainer

object TestDatabase {
    val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15").apply {
        withDatabaseName("test_db")
        withUsername("test_user")
        withPassword("test_pass")
    }

    fun start() {
        if (!postgresContainer.isRunning) {
            postgresContainer.start()
        }
    }

    fun stop() {
        if (postgresContainer.isRunning) {
            postgresContainer.stop()
        }
    }
}