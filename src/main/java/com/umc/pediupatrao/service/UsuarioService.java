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

// Gestão de usuários: todos os métodos são só para ADMIN
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

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario criarUsuario(Usuario dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário chamado " + username + ".");
        }
        validarSenha(dados.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setRole(validarPerfil(dados.getRole()).name());
        novo.setPassword(passwordEncoder.encode(dados.getPassword())); // senha criptografada
        Usuario salvo = usuarioRepository.save(novo);

        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(Map.of(), camposAuditados(salvo));
        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, "Usuario", salvo.getId(), alteracoes, null);
        return salvo;
    }

    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        Map<String, Object> antes = camposAuditados(existente);

        String username = validarUsername(dados.getUsername());
        Optional<Usuario> mesmoNome = usuarioRepository.findByUsername(username);
        if (mesmoNome.isPresent() && !mesmoNome.get().getId().equals(id)) {
            throw new RegraNegocioException("Já existe um usuário chamado " + username + ".");
        }

        // ninguém muda o próprio perfil (evita se dar mais permissão ou se trancar fora)
        Perfil novoPerfil = validarPerfil(dados.getRole());
        boolean ehEleMesmo = existente.getUsername().equals(UsuarioLogado.username());
        if (ehEleMesmo && !novoPerfil.name().equals(existente.getRole())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole()) && novoPerfil != Perfil.ADMIN) {
            garantirOutroAdmin(existente);
        }

        existente.setUsername(username);
        existente.setRole(novoPerfil.name());
        List<CampoAlterado> alteracoes = new ArrayList<>(AuditoriaService.diferencas(antes, camposAuditados(existente)));

        // senha em branco = mantém a atual. O valor da senha nunca vai para a auditoria.
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
            alteracoes.add(new CampoAlterado("senhaRedefinida", "não", "sim"));
        }

        Usuario salvo = usuarioRepository.save(existente);
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, "Usuario", id, alteracoes, null);
        }
        return salvo;
    }

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
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(camposAuditados(existente), Map.of());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, "Usuario", id, alteracoes, null);
    }

    // O sistema não pode ficar sem nenhum ADMIN
    private void garantirOutroAdmin(Usuario usuario) {
        for (Usuario u : usuarioRepository.findAll()) {
            if (Perfil.ADMIN.name().equals(u.getRole()) && !u.getId().equals(usuario.getId())) {
                return;
            }
        }
        throw new RegraNegocioException("O sistema precisa de pelo menos um ADMIN.");
    }

    private static Perfil validarPerfil(String role) {
        Perfil perfil = Perfil.deTexto(role);
        if (perfil == null) {
            throw new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    private static String validarUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("Informe o username.");
        }
        String limpo = username.trim().toLowerCase();
        // de 3 a 30 caracteres: letras, números, ponto, hífen ou _
        if (!limpo.matches("[a-z0-9._-]{3,30}")) {
            throw new RegraNegocioException("Username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou _).");
        }
        return limpo;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }

    private static Map<String, Object> camposAuditados(Usuario u) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("username", u.getUsername());
        campos.put("perfil", u.getRole());
        return campos;
    }
}
