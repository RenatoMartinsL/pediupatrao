package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {


    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoriaService;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("pedido1");
            }
            return p;
        });
        logarComo("atendente01", "ATENDENTE");
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static void logarComo(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(usuario, "x", "ROLE_" + perfil));
    }

    private Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private void clienteExistente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Marcos");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
    }

    private Pedido pedidoNoStatus(StatusPedido status) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteNome("Marcos");
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(new ItemPedido("pz", "Pizza", 2, new BigDecimal("50.00")));
        p.setItens(itens);
        PedidoService.recalcularValores(p);
        p.setStatus(status);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    // ---------- RF02: valores calculados no servidor ----------

    @Test
    void criarPedidoCalculaTotalComPrecoDoCadastroERegistraEntrada() {
        clienteExistente();
        produto("pz", "Pizza Calabresa", 52.90, true);
        produto("rf", "Refrigerante", 14.00, true);
        PedidoForm form = new PedidoForm();
        form.setClienteId("c1");
        form.getItens().add(new PedidoForm.Item("pz", 2));
        form.getItens().add(new PedidoForm.Item("rf", 1));

        Pedido pedido = service.criarPedido(form);

        assertEquals(new BigDecimal("119.80"), pedido.getSubtotal());
        assertEquals(new BigDecimal("119.80"), pedido.getValorTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("atendente01", pedido.getRegistradoPor());
        assertNotNull(pedido.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("Pedido"), eq("pedido1"), anyList(), isNull());
    }

    @Test
    void criarPedidoRejeitaQuantidadeInvalidaEProdutoInativo() {
        clienteExistente();
        produto("pz", "Pizza", 50, true);
        produto("old", "Pizza antiga", 30, false);

        PedidoForm qtdZero = new PedidoForm();
        qtdZero.setClienteId("c1");
        qtdZero.getItens().add(new PedidoForm.Item("pz", 0));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(qtdZero));

        PedidoForm inativo = new PedidoForm();
        inativo.setClienteId("c1");
        inativo.getItens().add(new PedidoForm.Item("old", 1));
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(inativo));

        PedidoForm vazio = new PedidoForm();
        vazio.setClienteId("c1");
        assertThrows(RegraNegocioException.class, () -> service.criarPedido(vazio));
    }

    // ---------- RF03: transições ----------

    @Test
    void transicaoInvalidaEhBloqueada() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", StatusPedido.FINALIZADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void saidaRegistraDataHoraEResponsavel() {
        pedidoNoStatus(StatusPedido.PRONTO);
        Pedido p = service.atualizarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA);
        assertEquals("atendente01", p.getResponsavelSaida());
        assertNotNull(p.getDataHoraSaida());
        verify(auditoriaService).registrar(eq(TipoOperacao.MUDANCA_STATUS_PEDIDO), eq("Pedido"), eq("p1"), anyList(), isNull());
    }

    @Test
    void naoMudaStatusDePedidoCancelado() {
        pedidoNoStatus(StatusPedido.CANCELADO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", StatusPedido.EM_PREPARACAO));
    }

    @Test
    void cancelarPeloFluxoDeStatusNaoEhPermitido() {
        pedidoNoStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", StatusPedido.CANCELADO));
    }

    // ---------- RF04: desconto e cancelamento ----------

    @Test
    void descontoRecalculaTotalEAudita() {
        logarComo("gerente01", "GERENTE");
        pedidoNoStatus(StatusPedido.EM_PREPARACAO); // subtotal 100,00
        Pedido p = service.aplicarDesconto("p1", new BigDecimal("15"), "atraso");
        assertEquals(new BigDecimal("15.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("85.00"), p.getValorTotal());
        assertEquals("gerente01", p.getDescontoAplicadoPor());
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("Pedido"), eq("p1"), anyList(), eq("atraso"));
    }

    @Test
    void descontoAcimaDe20PorCentoEhRejeitado() {
        pedidoNoStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("20.01"), null));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.ZERO, null));
    }

    @Test
    void descontoDepoisDaSaidaEhRejeitado() {
        pedidoNoStatus(StatusPedido.SAIU_PARA_ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.TEN, null));
    }

    @Test
    void cancelamentoExigeJustificativaEPreservaDados() {
        logarComo("gerente01", "GERENTE");
        pedidoNoStatus(StatusPedido.PRONTO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "  "));

        Pedido p = service.cancelar("p1", "Cliente desistiu do pedido");
        assertEquals(StatusPedido.CANCELADO, p.getStatus());
        assertEquals(StatusPedido.PRONTO, p.getStatusAntesCancelamento());
        assertEquals("gerente01", p.getCanceladoPor());
        assertEquals(new BigDecimal("100.00"), p.getValorTotal()); // valores preservados
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("Pedido"), eq("p1"), anyList(),
                eq("Cliente desistiu do pedido"));
    }

    @Test
    void cancelamentoDepoisDaSaidaEhRejeitado() {
        pedidoNoStatus(StatusPedido.RETIRADO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "Cliente desistiu do pedido"));
    }
}
