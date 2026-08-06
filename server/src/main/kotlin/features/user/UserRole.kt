package com.revio.server.features.user

/**
 * Mirrors the DB CHECK constraint chk_user_role (users.role, V1__init.sql). The column has
 * always existed but was never mapped in Exposed until Etapa 5 wired it into JWT issuance
 * (see AuthRoutes.kt / UserRoutes.kt). Promoting a user to ADMIN is SQL-only (see README) —
 * no app code ever sets this to ADMIN.
 */
enum class UserRole {
    USER,
    ADMIN,
}
