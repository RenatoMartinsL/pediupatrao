package com.umc.pediupatrao.dto;

/** Cancelamento de pedido: a justificativa é obrigatória. */
public record CancelamentoRequest(String justificativa) {
}
