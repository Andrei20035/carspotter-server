package com.carspotter.core.error

import io.ktor.http.HttpStatusCode

sealed class AuthApiException(
    val code: AuthErrorCode,
    val statusCode: HttpStatusCode,
    override val message: String
) : RuntimeException(message)

class AuthBadRequestException(
    code: AuthErrorCode,
    message: String
) : AuthApiException(code, HttpStatusCode.BadRequest, message)

class AuthUnauthorizedException(
    code: AuthErrorCode,
    message: String
) : AuthApiException(code, HttpStatusCode.Unauthorized, message)

class AuthForbiddenException(
    code: AuthErrorCode,
    message: String
) : AuthApiException(code, HttpStatusCode.Forbidden, message)

class AuthNotFoundException(
    code: AuthErrorCode,
    message: String
) : AuthApiException(code, HttpStatusCode.NotFound, message)

class AuthConflictException(
    code: AuthErrorCode,
    message: String
) : AuthApiException(code, HttpStatusCode.Conflict, message)
