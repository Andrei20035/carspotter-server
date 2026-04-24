package com.carspotter.features.auth

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*

interface IAuthDAO {
    suspend fun createCredentials(authCredential: AuthCredential): UUID
    suspend fun getCredentialsForLogin(email: String): AuthCredential?
    suspend fun getCredentialsById(credentialId: UUID): AuthCredential?
    suspend fun updatePassword(credentialId: UUID, newHashedPassword: String): Int
    suspend fun deleteCredentials(credentialId: UUID): Int
}

class AuthDAO : IAuthDAO {
    override suspend fun createCredentials(authCredential: AuthCredential): UUID {
        return transaction {
            AuthTable.insertReturning(listOf(AuthTable.id)) {
                it[email] = authCredential.email
                it[password] = authCredential.password
                it[provider] = authCredential.provider.name
                it[googleId] = authCredential.googleId
            }.singleOrNull()?.get(AuthTable.id)?.value ?: throw IllegalStateException("Failed to insert authCredential")
        }
    }

    override suspend fun getCredentialsForLogin(email: String): AuthCredential? {
        return transaction {
            AuthTable
                .selectAll()
                .where { AuthTable.email eq email }
                .limit(1)
                .map { row ->
                    AuthCredential(
                        id = row[AuthTable.id].value,
                        email = row[AuthTable.email],
                        password = row[AuthTable.password],
                        provider = AuthProvider.valueOf(row[AuthTable.provider].uppercase()),
                        googleId = row[AuthTable.googleId]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getCredentialsById(credentialId: UUID): AuthCredential? {
        return transaction {
            AuthTable
                .selectAll()
                .where { AuthTable.id eq credentialId }
                .limit(1)
                .map { row ->
                    AuthCredential(
                        id = row[AuthTable.id].value,
                        email = row[AuthTable.email],
                        password = row[AuthTable.password],
                        provider = AuthProvider.valueOf(row[AuthTable.provider].uppercase()),
                        googleId = row[AuthTable.googleId]
                    )
                }
        }.singleOrNull()
    }

    override suspend fun updatePassword(credentialId: UUID, newHashedPassword: String): Int {
        return transaction {
            AuthTable
                .update ({ AuthTable.id eq credentialId}) {
                    it[password] = newHashedPassword
            }
        }
    }

    override suspend fun deleteCredentials(credentialId: UUID): Int {
        return transaction {
            AuthTable
                .deleteWhere { id eq credentialId }
        }
    }
}