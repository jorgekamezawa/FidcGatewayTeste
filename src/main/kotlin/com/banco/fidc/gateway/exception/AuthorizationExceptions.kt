package com.banco.fidc.gateway.exception

open class AuthorizationException(message: String) : RuntimeException(message)

class InsufficientPermissionsException(
    message: String,
    val requiredPermissions: List<String>,
    val userPermissions: List<String>
) : RuntimeException(message)