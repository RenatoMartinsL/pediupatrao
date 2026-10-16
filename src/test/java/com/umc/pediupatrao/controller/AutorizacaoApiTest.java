package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prova que a autorização está no backend: as chamadas diretas às APIs são bloqueadas
 * conforme o perfil, independentemente do que a tela mostra.
 */
@WebMvcTest(controllers = {PedidoController.class, AuditoriaController.class, UsuarioController.class,
    ClienteController.class})
@Import(SecurityConfig.class)
class AutorizacaoApiTest {

    private static final String DESCONTO = "{\"percentual\": 10, \"justificativa\": \"teste\"}";
    private static final String CANCELAMENTO = "{\"justificativa\": \"Cliente desistiu do pedido\"}";

    @Autowired MockMvc mvc;

    @MockitoBean PedidoService pedidoService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    void somenteGerenteAplicaDesconto() throws Exception {
        Mockito.when(pedidoService.aplicarDesconto(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new Pedido());

        mvc.perform(post("/api/pedidos/p1/desconto").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isOk());
    }

    @Test
    void somenteGerenteCancela() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/cancelar").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditoriaSoParaAdminEGerente() throws Exception {
        mvc.perform(get("/api/auditoria").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(delete("/api/auditoria/x").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestaoDeUsuariosSoParaAdmin() throws Exception {
        String novoAdmin = "{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/usuarios").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(novoAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void gerenteNaoAlteraClienteEAdminNaoRegistraPedido() throws Exception {
        mvc.perform(post("/api/clientes").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\",\"telefone\":\"11999999999\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"c1\",\"itens\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void semTokenCsrfARequisicaoEhBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE"))
                .contentType(MediaType.APPLICATION_JSON).content(DESCONTO))
                .andExpect(status().isForbidden());
    }
}
