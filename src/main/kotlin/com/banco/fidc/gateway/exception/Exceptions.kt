package com.banco.fidc.gateway.exception

open class SessionValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

open class AuthorizationException(message: String) : RuntimeException(message)

class SessionNotFoundException(sessionId: String) : SessionValidationException("Session not found: $sessionId")

class InvalidJwtTokenException(message: String) : SessionValidationException("Invalid JWT token: $message")

class PermissionDeniedException(requiredPermissions: List<String>, userPermissions: List<String>) : 
    AuthorizationException("Required permissions: $requiredPermissions, but user has: $userPermissions")

class RedisConnectionException(message: String, cause: Throwable) : RuntimeException("Redis connection error: $message", cause)

class SessionParseException(message: String, cause: Throwable) : RuntimeException(message, cause)

class RedisUnavailableException(message: String, cause: Throwable) : RuntimeException(message, cause)