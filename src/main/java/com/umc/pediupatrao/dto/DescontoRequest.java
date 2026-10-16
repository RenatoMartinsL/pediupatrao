package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

// Corpo da requisição de desconto
public record DescontoRequest(BigDecimal percentual, String justificativa) {
}
