package com.revio.server.core.storage

data class R2Config(
    val accountId: String,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val publicBaseUrl: String,
    /** Overrides the derived R2 endpoint. Test-only (e.g. pointing at a local S3-compatible container); null in production. */
    val endpointOverride: String? = null,
) {
    companion object {
        fun fromEnv(): R2Config {
            fun required(name: String): String =
                System.getenv(name)?.takeIf { it.isNotBlank() }
                    ?: error("Missing required environment variable: $name")

            return R2Config(
                accountId = required("R2_ACCOUNT_ID"),
                bucket = required("R2_BUCKET"),
                accessKeyId = required("R2_ACCESS_KEY_ID"),
                secretAccessKey = required("R2_SECRET_ACCESS_KEY"),
                publicBaseUrl = required("R2_PUBLIC_BASE_URL").removeSuffix("/"),
            )
        }
    }
}
