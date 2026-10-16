package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock AuditoriaRepository repository;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void diferencasRetornaSoCamposAlterados() {
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("logradouro", "Rua das Flores");
        antes.put("telefone", "(11) 99999-1234");
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("logradouro", "Av. Brasil");
        depois.put("telefone", "(11) 99999-1234");

        List<CampoAlterado> d = AuditoriaService.diferencas(antes, depois);

        assertEquals(1, d.size());
        assertEquals(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil"), d.get(0));
    }

    @Test
    void registrarUsaUsuarioPerfilEDataHoraENaoGravaSenha() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("atendente01", "x", "ROLE_ATENDENTE"));
        when(repository.insert(any(RegistroAuditoria.class))).thenAnswer(inv -> inv.getArgument(0));
        AuditoriaService service = new AuditoriaService(repository);

        RegistroAuditoria r = service.registrar(TipoOperacao.ALTERACAO_USUARIO, "Usuario", "u1",
                List.of(new CampoAlterado("password", "hash1", "hash2"), new CampoAlterado("perfil", "ATENDENTE", "GERENTE")),
                null);

        assertEquals("atendente01", r.getUsuario());
        assertEquals("ATENDENTE", r.getPerfil());
        assertNotNull(r.getDataHora());
        assertEquals(1, r.getAlteracoes().size());
        assertTrue(r.getAlteracoes().stream().noneMatch(a -> a.campo().equals("password")));
    }

    @Test
    void telefoneEhMascaradoNaConsulta() {
        assertEquals("****-1234", AuditoriaService.mascararTelefone("(11) 99999-1234"));
    }
}
