package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Telas de pedidos (as regras ficam no PedidoService; aqui só mostramos as mensagens)
@Controller
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final AuditoriaService auditoriaService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService, AuditoriaService auditoriaService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.auditoriaService = auditoriaService;
    }

    // Uma coluna do quadro de pedidos
    public record Coluna(String titulo, List<Pedido> pedidos) {
    }

    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        List<Coluna> colunas = List.of(
                new Coluna("Recebido", filtrar(pedidos, StatusPedido.RECEBIDO)),
                new Coluna("Em preparação", filtrar(pedidos, StatusPedido.EM_PREPARACAO)),
                new Coluna("Pronto", filtrar(pedidos, StatusPedido.PRONTO)),
                new Coluna("Na rua / retirado", filtrar(pedidos, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO)));
        model.addAttribute("colunas", colunas);
        model.addAttribute("encerrados", filtrar(pedidos, StatusPedido.FINALIZADO, StatusPedido.CANCELADO));
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedido(Model model) {
        PedidoForm form = new PedidoForm();
        form.getItens().add(new PedidoForm.Item(null, 1)); // começa com uma linha de item
        model.addAttribute("pedidoForm", form);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/pedidos/salvar")
    public String salvarPedido(@ModelAttribute PedidoForm pedidoForm, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.criarPedido(pedidoForm);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado.");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id);
            model.addAttribute("pedido", pedido);
            model.addAttribute("proximos", new ArrayList<>(pedido.getStatus().proximos()));
            model.addAttribute("descontoMaximo", PedidoService.DESCONTO_MAXIMO);
            // a auditoria do pedido só aparece para quem pode ver auditoria
            if (UsuarioLogado.tem(Perfil.ADMIN) || UsuarioLogado.tem(Perfil.GERENTE)) {
                model.addAttribute("auditoria", auditoriaService.historicoDe("Pedido", id));
            }
            model.addAttribute("content", "pedidos/detalhe :: content");
            return "layout";
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
    }

    @PostMapping("/pedidos/{id}/status")
    public String mudarStatus(@PathVariable String id, @RequestParam StatusPedido status,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Status alterado para " + status.getDescricao() + ".");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String desconto(@PathVariable String id, @RequestParam BigDecimal percentual,
            @RequestParam(required = false) String justificativa, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.aplicarDesconto(id, percentual, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto aplicado.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelar(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private static List<Pedido> filtrar(List<Pedido> pedidos, StatusPedido... status) {
        List<StatusPedido> aceitos = List.of(status);
        List<Pedido> resultado = new ArrayList<>();
        for (Pedido p : pedidos) {
            if (aceitos.contains(p.getStatus())) {
                resultado.add(p);
            }
        }
        return resultado;
    }
}
