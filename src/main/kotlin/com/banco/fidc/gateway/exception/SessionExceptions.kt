package com.banco.fidc.gateway.exception

open class SessionValidationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class SessionNotFoundException(message: String) : RuntimeException(message)

class SessionParseException(message: String, cause: Throwable) : RuntimeException(message, cause)