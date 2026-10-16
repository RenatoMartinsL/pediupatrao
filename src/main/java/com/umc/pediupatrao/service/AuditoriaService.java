package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    /** Campos que nunca podem ir para a auditoria. */
    private static final Set<String> CAMPOS_SENSIVEIS = Set.of("password", "senha", "token", "credencial");
    private static final Set<String> CAMPOS_TELEFONE = Set.of("telefone");
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AuditoriaRepository repository;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Registra um evento com o usuário autenticado, seu perfil e a data/hora atual. */
    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        List<CampoAlterado> filtradas = alteracoes == null ? List.of() : alteracoes.stream()
                .filter(a -> !CAMPOS_SENSIVEIS.contains(a.campo().toLowerCase()))
                .toList();
        RegistroAuditoria registro = RegistroAuditoria.novo(
                UsuarioLogado.username(), UsuarioLogado.perfil(), LocalDateTime.now(clock),
                operacao, entidade, entidadeId, filtradas, justificativa);
        return repository.insert(registro);
    }

    /**
     * Compara dois "retratos" (campo -> valor) e devolve só os campos que mudaram.
     * Campos ausentes em um dos lados são tratados como vazios.
     */
    public static List<CampoAlterado> diferencas(Map<String, ?> anterior, Map<String, ?> novo) {
        Set<String> campos = new LinkedHashSet<>();
        if (anterior != null) {
            campos.addAll(anterior.keySet());
        }
        if (novo != null) {
            campos.addAll(novo.keySet());
        }
        List<CampoAlterado> mudancas = new ArrayList<>();
        for (String campo : campos) {
            String antes = texto(anterior == null ? null : anterior.get(campo));
            String depois = texto(novo == null ? null : novo.get(campo));
            if (!Objects.equals(antes, depois)) {
                mudancas.add(new CampoAlterado(campo, antes, depois));
            }
        }
        return mudancas;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> consultar(String entidade, String operacao, String usuario) {
        return repository.findAllByOrderByDataHoraDesc().stream()
                .filter(r -> vazio(entidade) || r.getEntidade().equalsIgnoreCase(entidade))
                .filter(r -> vazio(operacao) || r.getOperacao().name().equalsIgnoreCase(operacao))
                .filter(r -> vazio(usuario) || r.getUsuario().toLowerCase().contains(usuario.toLowerCase()))
                .map(AuditoriaService::protegerDadosPessoais)
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> historicoDe(String entidade, String entidadeId) {
        return repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId).stream()
                .map(AuditoriaService::protegerDadosPessoais)
                .toList();
    }

    /**
     * Na consulta, o telefone aparece parcialmente (só os 4 últimos dígitos): o objetivo da auditoria
     * é saber que houve alteração e quem a fez, não divulgar o dado pessoal completo.
     */
    static RegistroAuditoria protegerDadosPessoais(RegistroAuditoria r) {
        List<CampoAlterado> protegidas = r.getAlteracoes().stream()
                .map(a -> CAMPOS_TELEFONE.contains(a.campo())
                        ? new CampoAlterado(a.campo(), mascararTelefone(a.valorAnterior()), mascararTelefone(a.valorNovo()))
                        : a)
                .toList();
        return new RegistroAuditoria(r.getId(), r.getUsuario(), r.getPerfil(), r.getDataHora(), r.getOperacao(),
                r.getEntidade(), r.getEntidadeId(), protegidas, r.getJustificativa());
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return telefone;
        }
        // troca por * todos os dígitos, exceto os 4 últimos, preservando a formatação
        int manter = 4;
        int totalDigitos = digitos.length();
        StringBuilder sb = new StringBuilder();
        int visto = 0;
        for (char c : telefone.toCharArray()) {
            if (Character.isDigit(c)) {
                visto++;
                sb.append(visto > totalDigitos - manter ? c : '*');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof LocalDateTime dataHora) {
            return dataHora.format(FORMATO_DATA_HORA);
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
