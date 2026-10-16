package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoNormalPermitido() {
        assertTrue(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeIrPara(StatusPedido.FINALIZADO));
        assertTrue(StatusPedido.RETIRADO.podeIrPara(StatusPedido.FINALIZADO));
    }

    @Test
    void transicoesInvalidasBloqueadas() {
        assertFalse(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.PRONTO));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.RECEBIDO));
        assertFalse(StatusPedido.FINALIZADO.podeIrPara(StatusPedido.RECEBIDO));
        for (StatusPedido destino : StatusPedido.values()) {
            assertFalse(StatusPedido.CANCELADO.podeIrPara(destino), "cancelado → " + destino);
        }
    }

    @Test
    void cancelamentoNaoFazParteDoFluxoNormal() {
        for (StatusPedido origem : StatusPedido.values()) {
            assertFalse(origem.podeIrPara(StatusPedido.CANCELADO));
        }
    }
}
