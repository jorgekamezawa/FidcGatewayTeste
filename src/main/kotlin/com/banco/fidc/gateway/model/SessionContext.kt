package com.banco.fidc.gateway.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionContext(
    val sessionId: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: String? = null,
    val partner: String,
    val sessionSecret: String,
    val userInfo: UserInfo,
    val fund: Fund,
    val relationshipList: List<Relationship> = emptyList(),
    val relationshipSelected: Relationship?,
    val permissions: List<String> = emptyList()
) {

    fun hasValidRelationship(): Boolean = relationshipSelected != null

    fun hasPermissions(requiredPermissions: List<String>): Boolean {
        return requiredPermissions.all { permission ->
            permissions.contains(permission)
        }
    }

    fun toHeaders(): Map<String, String> {
        return buildMap {
            put(GatewayHeaders.USER_DOCUMENT_NUMBER, userInfo.cpf)
            put(GatewayHeaders.USER_EMAIL, userInfo.email)
            put(GatewayHeaders.USER_NAME, userInfo.fullName)
            put(GatewayHeaders.FUND_ID, fund.id)
            put(GatewayHeaders.FUND_NAME, fund.name)
            put(GatewayHeaders.PARTNER, partner)
            put(GatewayHeaders.SESSION_ID, sessionId)
            
            relationshipSelected?.let { relationship ->
                put(GatewayHeaders.RELATIONSHIP_ID, relationship.id)
                put(GatewayHeaders.CONTRACT_NUMBER, relationship.contractNumber)
            }
            
            if (permissions.isNotEmpty()) {
                put(GatewayHeaders.USER_PERMISSIONS, permissions.joinToString(","))
            }
        }
    }

    companion object {
        fun buildRedisKey(partner: String, sessionId: String): String = 
            "fidc:session:$partner:$sessionId"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserInfo(
    val cpf: String,
    val fullName: String,
    val email: String,
    val birthDate: String? = null,
    val phoneNumber: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Fund(
    val id: String,
    val name: String,
    val type: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Relationship(
    val id: String,
    val type: String,
    val name: String,
    val status: String,
    val contractNumber: String
)