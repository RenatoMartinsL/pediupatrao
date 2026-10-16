package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/** Desconto percentual (0 a 20) aplicado pelo GERENTE. */
public record DescontoRequest(BigDecimal percentual, String justificativa) {
}
