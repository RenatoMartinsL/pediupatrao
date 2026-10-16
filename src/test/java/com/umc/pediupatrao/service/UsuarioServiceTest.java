package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock UsuarioRepository repository;
    @Mock AuditoriaService auditoriaService;
    UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repository, new BCryptPasswordEncoder(), auditoriaService);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "x", "ROLE_ADMIN"));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        when(repository.findById(id)).thenReturn(Optional.of(u));
        when(repository.findByUsername(username)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        usuario("1", "admin", "ADMIN");
        Usuario dados = new Usuario();
        dados.setUsername("admin");
        dados.setRole("ATENDENTE");
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("1", dados));
        verify(repository, never()).save(any());
    }

    @Test
    void perfilInexistenteEhRejeitado() {
        when(repository.findByUsername("novo")).thenReturn(Optional.empty());
        Usuario dados = new Usuario();
        dados.setUsername("novo");
        dados.setPassword("segredo123");
        dados.setRole("SUPERADMIN");
        assertThrows(RegraNegocioException.class, () -> service.criarUsuario(dados));
    }

    @Test
    void naoRemoveOUltimoAdmin() {
        Usuario outroAdmin = usuario("2", "admin2", "ADMIN");
        when(repository.findAll()).thenReturn(List.of(outroAdmin));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("2"));
    }
}
