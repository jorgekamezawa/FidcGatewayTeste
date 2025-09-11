package com.banco.fidc.gateway.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Contexto completo da sessão do usuário extraído do Redis
 * Contém todas as informações necessárias para validação e enriquecimento de headers
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionContext(
    @JsonProperty("sessionId")
    val sessionId: String,
    
    @JsonProperty("partner")
    val partner: String,
    
    @JsonProperty("userDocumentNumber")
    val userDocumentNumber: String,
    
    @JsonProperty("userEmail")
    val userEmail: String,
    
    @JsonProperty("userName")
    val userName: String,
    
    @JsonProperty("fundId")
    val fundId: String,
    
    @JsonProperty("fundName")
    val fundName: String,
    
    @JsonProperty("relationshipId")
    val relationshipId: String?,
    
    @JsonProperty("contractNumber")
    val contractNumber: String?,
    
    @JsonProperty("userPermissions")
    val userPermissions: List<String> = emptyList(),
    
    @JsonProperty("sessionSecret")
    val sessionSecret: String
) {

    /**
     * Valida se a sessão possui relacionamento selecionado (obrigatório)
     */
    fun hasValidRelationship(): Boolean = !relationshipId.isNullOrBlank()

    /**
     * Verifica se o usuário possui todas as permissões necessárias
     */
    fun hasPermissions(requiredPermissions: List<String>): Boolean {
        return requiredPermissions.all { permission ->
            userPermissions.contains(permission)
        }
    }

    /**
     * Verifica se o partner da sessão corresponde ao esperado
     */
    fun isPartnerValid(expectedPartner: String): Boolean {
        return partner.equals(expectedPartner, ignoreCase = true)
    }

    /**
     * Converte o contexto da sessão para headers HTTP
     * Estes headers serão injetados automaticamente em todas as requisições downstream
     */
    fun toHeaders(): Map<String, String> {
        return buildMap {
            put("userDocumentNumber", userDocumentNumber)
            put("userEmail", userEmail)
            put("userName", userName)
            put("fundId", fundId)
            put("fundName", fundName)
            put("partner", partner)
            put("sessionId", sessionId)
            
            // Headers opcionais (podem ser null)
            relationshipId?.let { put("relationshipId", it) }
            contractNumber?.let { put("contractNumber", it) }
            
            // Permissões como string separada por vírgula
            if (userPermissions.isNotEmpty()) {
                put("userPermissions", userPermissions.joinToString(","))
            }
        }
    }

    /**
     * Chave Redis para busca desta sessão
     */
    fun getRedisKey(): String = "fidc:session:$partner:$sessionId"

    companion object {
        /**
         * Cria a chave Redis para busca de sessão
         */
        fun buildRedisKey(partner: String, sessionId: String): String = 
            "fidc:session:$partner:$sessionId"
    }
}