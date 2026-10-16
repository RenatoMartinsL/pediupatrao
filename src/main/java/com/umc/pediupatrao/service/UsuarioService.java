package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Gestão de usuários: exclusiva do ADMIN (validado aqui e também no SecurityConfig).
 * Impede autoelevação/autorrebaixamento e a remoção do último administrador.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Lista todos os usuários
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Cria um novo usuário (criptografando a senha)
    public Usuario criarUsuario(Usuario dados) {
        String username = normalizarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
        }
        validarSenha(dados.getPassword(), true);

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setRole(validarPerfil(dados.getRole()).name());
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        Usuario salvo = usuarioRepository.save(novo);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, "Usuario", salvo.getId(),
                AuditoriaService.diferencas(Map.of(), retrato(salvo)), null);
        return salvo;
    }

    // Atualiza username, perfil e (opcionalmente) a senha
    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        Map<String, Object> antes = retrato(existente);

        String username = normalizarUsername(dados.getUsername());
        usuarioRepository.findByUsername(username)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
                });

        Perfil novoPerfil = validarPerfil(dados.getRole());
        boolean ehOProprioUsuario = existente.getUsername().equals(UsuarioLogado.username());
        if (ehOProprioUsuario && !novoPerfil.name().equals(existente.getRole())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && novoPerfil != Perfil.ADMIN) {
            garantirOutroAdmin(existente);
        }

        existente.setUsername(username);
        existente.setRole(novoPerfil.name());

        List<CampoAlterado> alteracoes = new ArrayList<>(AuditoriaService.diferencas(antes, retrato(existente)));
        // Só atualiza a senha se uma nova foi informada (o valor nunca vai para a auditoria)
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword(), true);
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
            alteracoes.add(new CampoAlterado("senhaRedefinida", "não", "sim"));
        }

        Usuario salvo = usuarioRepository.save(existente);
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, "Usuario", id, alteracoes, null);
        }
        return salvo;
    }

    // Remove um usuário pelo ID
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(UsuarioLogado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole())) {
            garantirOutroAdmin(existente);
        }
        usuarioRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, "Usuario", id,
                AuditoriaService.diferencas(retrato(existente), Map.of()), null);
    }

    private void garantirOutroAdmin(Usuario usuario) {
        long admins = usuarioRepository.findAll().stream()
                .filter(u -> Perfil.ADMIN.name().equals(u.getRole()))
                .filter(u -> !u.getId().equals(usuario.getId()))
                .count();
        if (admins == 0) {
            throw new RegraNegocioException("O sistema precisa de pelo menos um ADMIN.");
        }
    }

    private static Perfil validarPerfil(String role) {
        Perfil perfil = Perfil.deTexto(role);
        if (perfil == null) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    private static String normalizarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        String limpo = username.trim().toLowerCase();
        if (!limpo.matches("[a-z0-9._-]{3,30}")) {
            throw new RegraNegocioException("Username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou _).");
        }
        return limpo;
    }

    private static void validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new RegraNegocioException("Informe a senha.");
            }
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }

    private static Map<String, Object> retrato(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("perfil", u.getRole());
        return m;
    }
}
