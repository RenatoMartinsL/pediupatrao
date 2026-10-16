package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema. O nome do perfil vira a role do Spring Security (ROLE_ADMIN, ...).
 */
public enum Perfil {
    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Converte o texto salvo no banco; retorna null para valores desconhecidos (ex.: o antigo "USER"). */
    public static Perfil deTexto(String valor) {
        if (valor == null) {
            return null;
        }
        for (Perfil p : values()) {
            if (p.name().equalsIgnoreCase(valor.trim())) {
                return p;
            }
        }
        return null;
    }
}
