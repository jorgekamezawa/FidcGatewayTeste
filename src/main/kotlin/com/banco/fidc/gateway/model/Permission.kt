package com.banco.fidc.gateway.model

/**
 * Enum com todas as permissões do sistema FIDC
 * Centraliza as permissões para facilitar manutenção e validação
 */
enum class Permission(val code: String, val description: String) {
    // Permissões de Simulação
    CREATE_SIMULATION("CREATE_SIMULATION", "Criar simulações de crédito"),
    VIEW_SIMULATION_RESULTS("VIEW_SIMULATION_RESULTS", "Visualizar resultados de simulações"),
    DELETE_SIMULATION("DELETE_SIMULATION", "Excluir simulações"),
    
    // Permissões de Contratação
    CREATE_CONTRACT("CREATE_CONTRACT", "Criar contratações"),
    APPROVE_CONTRACT("APPROVE_CONTRACT", "Aprovar contratações"),
    CANCEL_CONTRACT("CANCEL_CONTRACT", "Cancelar contratações"),
    
    // Permissões de Perfil
    VIEW_PROFILE("VIEW_PROFILE", "Visualizar perfil do usuário"),
    UPDATE_PROFILE("UPDATE_PROFILE", "Atualizar perfil do usuário"),
    
    // Permissões de Consulta
    VIEW_STATEMENTS("VIEW_STATEMENTS", "Visualizar extratos"),
    VIEW_DOCUMENTS("VIEW_DOCUMENTS", "Visualizar documentos"),
    DOWNLOAD_DOCUMENTS("DOWNLOAD_DOCUMENTS", "Baixar documentos"),
    
    // Permissões Administrativas
    ADMIN_PANEL("ADMIN_PANEL", "Acesso ao painel administrativo"),
    MANAGE_USERS("MANAGE_USERS", "Gerenciar usuários"),
    VIEW_REPORTS("VIEW_REPORTS", "Visualizar relatórios");

    companion object {
        /**
         * Busca permissão pelo código
         */
        fun fromCode(code: String): Permission? = 
            values().find { it.code.equals(code, ignoreCase = true) }

        /**
         * Valida se uma lista de códigos são permissões válidas
         */
        fun areValidCodes(codes: List<String>): Boolean =
            codes.all { code -> fromCode(code) != null }

        /**
         * Converte lista de códigos para lista de permissões
         */
        fun fromCodes(codes: List<String>): List<Permission> =
            codes.mapNotNull { fromCode(it) }
    }
}