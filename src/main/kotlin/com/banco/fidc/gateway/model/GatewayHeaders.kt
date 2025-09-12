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
    const val REQUEST_START_TIME = "X-Request-Start-Time"
}