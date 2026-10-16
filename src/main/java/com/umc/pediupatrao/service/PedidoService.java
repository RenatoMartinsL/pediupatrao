package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.MudancaStatus;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    private static final int QUANTIDADE_MAXIMA = 50;
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    // ========================
    // CONSULTAS
    // ========================
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        // mais recentes primeiro
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
    // NOVO PEDIDO (entrada)
    // ========================
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoForm form) {
        Cliente cliente = buscarCliente(form.getClienteId());
        LocalDateTime agora = LocalDateTime.now();
        String usuario = UsuarioLogado.username();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setObservacao(textoOuNulo(form.getObservacao()));
        pedido.setItens(montarItens(form.getItens()));
        recalcularValores(pedido);

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setRegistradoPor(usuario);
        pedido.setDataHoraEntrada(agora);
        pedido.getHistoricoStatus().add(new MudancaStatus(StatusPedido.RECEBIDO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(Map.of(), camposAuditados(salvo));
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, "Pedido", salvo.getId(), alteracoes, null);
        return salvo;
    }

    // Itens só podem ser trocados enquanto o pedido não foi para a cozinha
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarPedido(String id, PedidoForm form) {
        Pedido pedido = buscarPorId(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException("Só é possível alterar itens de pedidos com status RECEBIDO.");
        }
        Map<String, Object> antes = camposAuditados(pedido);

        if (form.getClienteId() != null && !form.getClienteId().equals(pedido.getClienteId())) {
            Cliente cliente = buscarCliente(form.getClienteId());
            pedido.setClienteId(cliente.getId());
            pedido.setClienteNome(cliente.getNome());
        }
        pedido.setItens(montarItens(form.getItens()));
        pedido.setObservacao(textoOuNulo(form.getObservacao()));
        recalcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(antes, camposAuditados(salvo));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, "Pedido", id, alteracoes, null);
        }
        return salvo;
    }

    // ========================
    // MUDANÇA DE STATUS
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
            throw new RegraNegocioException("Para cancelar, use a opção de cancelamento (só o gerente pode).");
        }
        if (!atual.podeIrPara(novoStatus)) {
            throw new RegraNegocioException("Não é possível passar de " + atual.getDescricao()
                    + " para " + novoStatus.getDescricao() + ".");
        }

        Map<String, Object> antes = camposAuditados(pedido);
        LocalDateTime agora = LocalDateTime.now();
        String usuario = UsuarioLogado.username();

        pedido.setStatus(novoStatus);
        pedido.getHistoricoStatus().add(new MudancaStatus(novoStatus, agora, usuario));

        // saída do pedido: grava hora e quem liberou
        if (novoStatus == StatusPedido.SAIU_PARA_ENTREGA || novoStatus == StatusPedido.RETIRADO) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setFinalizadoPor(usuario);
        }

        Pedido salvo = pedidoRepository.save(pedido);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(antes, camposAuditados(salvo));
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, "Pedido", id, alteracoes, null);
        return salvo;
    }

    // ========================
    // DESCONTO (só gerente)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String justificativa) {
        Pedido pedido = buscarPorId(id);
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Desconto só pode ser dado antes do pedido sair.");
        }
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Informe um percentual de desconto maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto máximo é de " + DESCONTO_MAXIMO + "%.");
        }

        Map<String, Object> antes = camposAuditados(pedido);
        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        pedido.setDescontoAplicadoPor(UsuarioLogado.username());
        pedido.setDataHoraDesconto(LocalDateTime.now());
        pedido.setJustificativaDesconto(textoOuNulo(justificativa));
        recalcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(antes, camposAuditados(salvo));
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, "Pedido", id, alteracoes,
                salvo.getJustificativaDesconto());
        return salvo;
    }

    // ========================
    // CANCELAMENTO (só gerente)
    // ========================
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscarPorId(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Este pedido já está cancelado.");
        }
        if (!pedido.getStatus().isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível cancelar: o pedido já saiu.");
        }
        String motivo = textoOuNulo(justificativa);
        if (motivo == null || motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe o motivo do cancelamento (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }

        Map<String, Object> antes = camposAuditados(pedido);
        LocalDateTime agora = LocalDateTime.now();
        String usuario = UsuarioLogado.username();

        // itens e valores ficam como estavam, só muda o status
        pedido.setStatusAntesCancelamento(pedido.getStatus());
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoPor(usuario);
        pedido.setDataHoraCancelamento(agora);
        pedido.setJustificativaCancelamento(motivo);
        pedido.getHistoricoStatus().add(new MudancaStatus(StatusPedido.CANCELADO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(antes, camposAuditados(salvo));
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, "Pedido", id, alteracoes, motivo);
        return salvo;
    }

    // ========================
    // MÉTODOS AUXILIARES
    // ========================
    private Cliente buscarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não existe."));
    }

    // O preço vem sempre do cadastro do produto, nunca do formulário
    private List<ItemPedido> montarItens(List<PedidoForm.Item> itensDoForm) {
        List<ItemPedido> itens = new ArrayList<>();
        if (itensDoForm != null) {
            for (PedidoForm.Item item : itensDoForm) {
                if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()) {
                    continue; // linha vazia no formulário
                }
                Integer quantidade = item.getQuantidade();
                if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
                    throw new RegraNegocioException("Quantidade inválida: use de 1 a " + QUANTIDADE_MAXIMA + ".");
                }
                Produto produto = produtoRepository.findById(item.getProdutoId())
                        .orElseThrow(() -> new RegraNegocioException("Produto informado não existe."));
                if (!Boolean.TRUE.equals(produto.getAtivo())) {
                    throw new RegraNegocioException("O produto " + produto.getNome() + " está inativo.");
                }
                if (produto.getPreco() == null || produto.getPreco() < 0) {
                    throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço.");
                }
                BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
                itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco));
            }
        }
        if (itens.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa de pelo menos um item.");
        }
        return itens;
    }

    // subtotal = soma dos itens | desconto = subtotal x percentual | total = subtotal - desconto
    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            subtotal = subtotal.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal percentual = pedido.getPercentualDesconto() != null ? pedido.getPercentualDesconto() : BigDecimal.ZERO;
        BigDecimal desconto = subtotal.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
    }

    // Campos que entram na auditoria; comparando antes e depois sabemos o que mudou
    static Map<String, Object> camposAuditados(Pedido p) {
        List<String> itens = new ArrayList<>();
        for (ItemPedido item : p.getItens()) {
            itens.add(item.toString());
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("cliente", p.getClienteNome());
        campos.put("itens", String.join("; ", itens));
        campos.put("observacao", p.getObservacao());
        campos.put("subtotal", p.getSubtotal());
        campos.put("percentualDesconto", p.getPercentualDesconto());
        campos.put("valorDesconto", p.getValorDesconto());
        campos.put("valorTotal", p.getValorTotal());
        campos.put("status", p.getStatus());
        campos.put("registradoPor", p.getRegistradoPor());
        campos.put("dataHoraEntrada", p.getDataHoraEntrada());
        campos.put("responsavelSaida", p.getResponsavelSaida());
        campos.put("dataHoraSaida", p.getDataHoraSaida());
        campos.put("finalizadoPor", p.getFinalizadoPor());
        campos.put("canceladoPor", p.getCanceladoPor());
        return campos;
    }

    private static String textoOuNulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }
}
