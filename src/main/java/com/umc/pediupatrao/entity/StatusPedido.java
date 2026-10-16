package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

// Fluxo do pedido: RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA ou RETIRADO, FINALIZADO.
// CANCELADO fica fora do fluxo normal (ver PedidoService.cancelar).
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    // Para quais status o pedido pode ir a partir do atual
    public Set<StatusPedido> proximos() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeIrPara(StatusPedido destino) {
        return destino != null && proximos().contains(destino);
    }

    // O pedido saiu da pizzaria (entrega ou retirada)
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    // Ainda não saiu: pode receber desconto ou ser cancelado
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }
}
