package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.MudancaStatus;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos: cálculo de valores no servidor, fluxo de status,
 * desconto e cancelamento exclusivos do GERENTE, e auditoria de todas as mudanças.
 */
@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;
    static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;
    private static final String ENTIDADE = "Pedido";

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    // ========================
    // CONSULTAS
    // ========================
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll().stream()
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // ========================
    // ENTRADA (RECEBIDO)
    // ========================
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoForm form) {
        Cliente cliente = buscarCliente(form.getClienteId());
        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = UsuarioLogado.username();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setObservacao(limpar(form.getObservacao()));
        pedido.setItens(montarItens(form.getItens()));
        recalcularValores(pedido);

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setRegistradoPor(usuario);
        pedido.setDataHoraEntrada(agora);
        pedido.getHistoricoStatus().add(new MudancaStatus(StatusPedido.RECEBIDO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.diferencas(Map.of(), retrato(salvo)), null);
        return salvo;
    }

    /** Alteração de itens/observação: só enquanto o pedido ainda está RECEBIDO (antes de ir para a cozinha). */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarPedido(String id, PedidoForm form) {
        Pedido pedido = buscarPorId(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Só é possível alterar itens de pedidos com status RECEBIDO.");
        }
        Map<String, Object> antes = retrato(pedido);

        if (form.getClienteId() != null && !form.getClienteId().equals(pedido.getClienteId())) {
            Cliente cliente = buscarCliente(form.getClienteId());
            pedido.setClienteId(cliente.getId());
            pedido.setClienteNome(cliente.getNome());
        }
        pedido.setItens(montarItens(form.getItens()));
        pedido.setObservacao(limpar(form.getObservacao()));
        recalcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        var alteracoes = AuditoriaService.diferencas(antes, retrato(salvo));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, id, alteracoes, null);
        }
        return salvo;
    }

    // ========================
    // TRANSIÇÕES DE STATUS
    // ========================
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode mais ter o status alterado.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Use a operação de cancelamento (exclusiva do GERENTE, com justificativa).");
        }
        if (!atual.podeIrPara(novoStatus)) {
            throw new RegraNegocioException("Transição inválida: " + atual.getDescricao() + " → "
                    + novoStatus.getDescricao() + ". Próximos permitidos: " + descreverProximos(atual) + ".");
        }

        Map<String, Object> antes = retrato(pedido);
        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = UsuarioLogado.username();

        pedido.setStatus(novoStatus);
        pedido.getHistoricoStatus().add(new MudancaStatus(novoStatus, agora, usuario));
        if (novoStatus.isSaida()) {
            // Saída: data/hora e responsável são obrigatórios e preenchidos pelo servidor
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setFinalizadoPor(usuario);
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    // ========================
    // DESCONTO (somente GERENTE)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String justificativa) {
        Pedido pedido = buscarPorId(id);
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Informe um percentual de desconto maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de " + DESCONTO_MAXIMO + "%.");
        }
        if (percentual.scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual.");
        }

        Map<String, Object> antes = retrato(pedido);
        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        pedido.setDescontoAplicadoPor(UsuarioLogado.username());
        pedido.setDataHoraDesconto(LocalDateTime.now(clock));
        pedido.setJustificativaDesconto(limpar(justificativa));
        recalcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), salvo.getJustificativaDesconto());
        return salvo;
    }

    // ========================
    // CANCELAMENTO (somente GERENTE)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscarPorId(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Este pedido já está cancelado.");
        }
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível cancelar: o pedido já saiu para entrega/retirada.");
        }
        String motivo = limpar(justificativa);
        if (motivo == null || motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }

        Map<String, Object> antes = retrato(pedido);
        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = UsuarioLogado.username();

        // Os itens e valores são preservados como histórico; só muda o status e os dados do cancelamento
        pedido.setStatusAntesCancelamento(pedido.getStatus());
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoPor(usuario);
        pedido.setDataHoraCancelamento(agora);
        pedido.setJustificativaCancelamento(motivo);
        pedido.getHistoricoStatus().add(new MudancaStatus(StatusPedido.CANCELADO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, id,
                AuditoriaService.diferencas(antes, retrato(salvo)), motivo);
        return salvo;
    }

    // ========================
    // AUXILIARES
    // ========================
    private Cliente buscarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não existe."));
    }

    /** Monta os itens buscando nome e preço no cadastro de produtos (nunca do que veio na requisição). */
    private List<ItemPedido> montarItens(List<PedidoForm.Item> itensForm) {
        List<PedidoForm.Item> validos = itensForm == null ? List.of() : itensForm.stream()
                .filter(i -> i != null && i.getProdutoId() != null && !i.getProdutoId().isBlank())
                .toList();
        if (validos.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa de pelo menos um item.");
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (PedidoForm.Item item : validos) {
            Integer qtd = item.getQuantidade();
            if (qtd == null || qtd < 1 || qtd > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("Quantidade inválida: use de 1 a " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            Produto produto = produtoRepository.findById(item.getProdutoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto informado não existe."));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() < 0) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), qtd, preco));
        }
        return itens;
    }

    /** subtotal = soma dos itens; desconto = subtotal × percentual; total = subtotal − desconto. */
    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal desconto = subtotal.multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
    }

    /** "Retrato" dos campos auditáveis do pedido, usado para calcular valores anteriores e novos. */
    static Map<String, Object> retrato(Pedido p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cliente", p.getClienteNome());
        m.put("itens", p.getItens() == null ? null
                : p.getItens().stream().map(ItemPedido::toString).collect(Collectors.joining("; ")));
        m.put("observacao", p.getObservacao());
        m.put("subtotal", p.getSubtotal());
        m.put("percentualDesconto", p.getPercentualDesconto());
        m.put("valorDesconto", p.getValorDesconto());
        m.put("valorTotal", p.getValorTotal());
        m.put("status", p.getStatus());
        m.put("registradoPor", p.getRegistradoPor());
        m.put("dataHoraEntrada", p.getDataHoraEntrada());
        m.put("responsavelSaida", p.getResponsavelSaida());
        m.put("dataHoraSaida", p.getDataHoraSaida());
        m.put("finalizadoPor", p.getFinalizadoPor());
        m.put("canceladoPor", p.getCanceladoPor());
        return m;
    }

    private static String descreverProximos(StatusPedido atual) {
        var proximos = atual.proximos();
        return proximos.isEmpty() ? "nenhum"
                : proximos.stream().map(StatusPedido::getDescricao).collect(Collectors.joining(" ou "));
    }

    private static String limpar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}
