package com.umc.pediupatrao.entity;

// Perfis de acesso. No Spring Security viram ROLE_ADMIN, ROLE_GERENTE e ROLE_ATENDENTE.
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

    // Converte o texto do banco em Perfil (devolve null se for inválido, como o antigo "USER")
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
