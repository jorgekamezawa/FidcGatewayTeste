package com.banco.fidc.gateway.model

object AllowedHeaders {
    
    private val ALLOWED_HEADERS = setOf(
        "accept",
        "accept-charset", 
        "accept-encoding",
        "accept-language",
        "content-type",
        "content-length",
        "x-correlation-id",
        "x-trace-id", 
        "x-request-id",
        "x-span-id",
        "x-client-version",
        "x-api-version",
        "cache-control",
        "if-none-match",
        "if-modified-since"
    )
    
    fun isAllowed(headerName: String): Boolean {
        return ALLOWED_HEADERS.contains(headerName.lowercase())
    }
}