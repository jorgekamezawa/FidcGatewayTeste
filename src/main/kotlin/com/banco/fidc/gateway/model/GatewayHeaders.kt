package com.banco.fidc.gateway.model

/**
 * Constantes para headers do gateway
 * Centraliza todos os nomes de headers usados no sistema
 */
object GatewayHeaders {
    
    // Headers de entrada (vindos do frontend)
    const val AUTHORIZATION = "Authorization"
    const val PARTNER_HEADER = "partner"
    const val CORRELATION_ID = "X-Correlation-ID"
    
    // Headers injetados pelo gateway (para downstream services)
    const val USER_DOCUMENT_NUMBER = "userDocumentNumber"
    const val USER_EMAIL = "userEmail"
    const val USER_NAME = "userName"
    const val FUND_ID = "fundId"
    const val FUND_NAME = "fundName"
    const val PARTNER = "partner"
    const val RELATIONSHIP_ID = "relationshipId"
    const val CONTRACT_NUMBER = "contractNumber"
    const val SESSION_ID = "sessionId"
    const val USER_PERMISSIONS = "userPermissions"
    
    // Headers internos do gateway
    const val GATEWAY_PROCESSED = "X-Gateway-Processed"
    const val REQUEST_START_TIME = "X-Request-Start-Time"
    
    /**
     * Lista de todos os headers injetados automaticamente
     */
    val INJECTED_HEADERS = listOf(
        USER_DOCUMENT_NUMBER,
        USER_EMAIL,
        USER_NAME,
        FUND_ID,
        FUND_NAME,
        PARTNER,
        RELATIONSHIP_ID,
        CONTRACT_NUMBER,
        SESSION_ID,
        USER_PERMISSIONS
    )
    
    /**
     * Headers que devem ser removidos da resposta (segurança)
     */
    val HEADERS_TO_REMOVE_FROM_RESPONSE = listOf(
        "X-Internal-User-Id",
        "X-Internal-Session-Secret",
        "X-Debug-Info"
    )
}