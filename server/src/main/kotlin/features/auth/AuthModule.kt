package com.carspotter.features.auth

import org.koin.dsl.module

val authModule = module {

    single<IAuthDAO> { AuthDAO() }
    single<GoogleTokenVerifier> { GoogleTokenVerifierImpl() }
    single<IAuthService> { AuthService(get(), get(), get()) }

    single {
        val secret = System.getenv("JWT_SECRET")
            ?: error("JWT_SECRET environment variable is not set")
        val issuer = System.getenv("JWT_ISSUER")
            ?: error("JWT_ISSUER environment variable is not set")
        val audience = System.getenv("JWT_AUDIENCE")
            ?: error("JWT_AUDIENCE environment variable is not set")
        JwtService(secret, issuer, audience)
    }

}
