package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Telas de início, usuários, clientes e produtos
@Controller
public class HomeController {

    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;
    private final PedidoService pedidoService;

    public HomeController(ClienteService clienteService, ProdutoService produtoService,
            UsuarioService usuarioService, PedidoService pedidoService) {
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
        this.pedidoService = pedidoService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        // números do painel inicial
        LocalDate hoje = LocalDate.now();
        int pedidosHoje = 0;
        int emAndamento = 0;
        int prontos = 0;
        BigDecimal finalizadoHoje = BigDecimal.ZERO;

        for (Pedido p : pedidoService.listarPedidos()) {
            boolean ehDeHoje = p.getDataHoraEntrada() != null && p.getDataHoraEntrada().toLocalDate().equals(hoje);
            if (ehDeHoje) {
                pedidosHoje++;
            }
            if (p.getStatus().isAntesDaSaida()) {
                emAndamento++;
            }
            if (p.getStatus() == StatusPedido.PRONTO) {
                prontos++;
            }
            if (ehDeHoje && p.getStatus() == StatusPedido.FINALIZADO) {
                finalizadoHoje = finalizadoHoje.add(p.getValorTotal());
            }
        }

        model.addAttribute("hoje", hoje.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale.of("pt", "BR"))));
        model.addAttribute("pedidosHoje", pedidosHoje);
        model.addAttribute("emAndamento", emAndamento);
        model.addAttribute("prontos", prontos);
        model.addAttribute("faturadoHoje", finalizadoHoje);
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado(Model model) {
        model.addAttribute("content", "acesso-negado :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        return usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null); // o hash da senha não vai para o formulário
            model.addAttribute("usuario", u);
            model.addAttribute("perfis", Perfil.values());
            model.addAttribute("content", "usuarios/form :: content");
            return "layout";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        });
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.criarUsuario(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios/novo";
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("produto", new Produto());
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    @PostMapping("/produtos/salvar")
    public String salvarProduto(@ModelAttribute Produto produto, RedirectAttributes redirectAttributes) {
        try {
            produtoService.salvar(produto);
            redirectAttributes.addFlashAttribute("sucesso", "Produto salvo com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/excluir/{id}")
    public String excluirProduto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        produtoService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Produto removido.");
        return "redirect:/produtos";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        try {
            cliente.setId(null);
            clienteService.salvar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/novo";
        }
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        return clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            model.addAttribute("content", "clientes/form :: content");
            return "layout";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        });
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        try {
            cliente.setId(id);
            clienteService.salvar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes/editar/" + id;
        }
        return "redirect:/clientes";
    }

    @PostMapping("/clientes/excluir/{id}")
    public String excluirCliente(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            clienteService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente excluído.");
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }
}
