package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
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

    // estes campos nunca são gravados na auditoria
    private static final Set<String> CAMPOS_PROIBIDOS = Set.of("password", "senha", "token");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AuditoriaRepository repository;

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    // Grava um registro com o usuário logado, o perfil dele e a hora atual
    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        List<CampoAlterado> permitidas = new ArrayList<>();
        if (alteracoes != null) {
            for (CampoAlterado alteracao : alteracoes) {
                if (!CAMPOS_PROIBIDOS.contains(alteracao.campo().toLowerCase())) {
                    permitidas.add(alteracao);
                }
            }
        }
        RegistroAuditoria registro = new RegistroAuditoria(null, UsuarioLogado.username(), UsuarioLogado.perfil(),
                LocalDateTime.now(), operacao, entidade, entidadeId, permitidas, justificativa);
        return repository.insert(registro);
    }

    // Compara os campos de antes e de depois e devolve só os que mudaram
    public static List<CampoAlterado> diferencas(Map<String, ?> antes, Map<String, ?> depois) {
        Set<String> campos = new LinkedHashSet<>();
        campos.addAll(antes.keySet());
        campos.addAll(depois.keySet());

        List<CampoAlterado> mudancas = new ArrayList<>();
        for (String campo : campos) {
            String valorAntes = paraTexto(antes.get(campo));
            String valorDepois = paraTexto(depois.get(campo));
            if (!Objects.equals(valorAntes, valorDepois)) {
                mudancas.add(new CampoAlterado(campo, valorAntes, valorDepois));
            }
        }
        return mudancas;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> consultar(String entidade, String operacao, String usuario) {
        List<RegistroAuditoria> resultado = new ArrayList<>();
        for (RegistroAuditoria r : repository.findAllByOrderByDataHoraDesc()) {
            boolean entidadeOk = vazio(entidade) || r.getEntidade().equalsIgnoreCase(entidade);
            boolean operacaoOk = vazio(operacao) || r.getOperacao().name().equalsIgnoreCase(operacao);
            boolean usuarioOk = vazio(usuario) || r.getUsuario().toLowerCase().contains(usuario.toLowerCase());
            if (entidadeOk && operacaoOk && usuarioOk) {
                resultado.add(esconderTelefone(r));
            }
        }
        return resultado;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> historicoDe(String entidade, String entidadeId) {
        List<RegistroAuditoria> resultado = new ArrayList<>();
        for (RegistroAuditoria r : repository.findByEntidadeAndEntidadeIdOrderByDataHoraDesc(entidade, entidadeId)) {
            resultado.add(esconderTelefone(r));
        }
        return resultado;
    }

    // Na tela o telefone aparece só com os 4 últimos dígitos (proteção de dado pessoal)
    private static RegistroAuditoria esconderTelefone(RegistroAuditoria r) {
        List<CampoAlterado> alteracoes = new ArrayList<>();
        for (CampoAlterado a : r.getAlteracoes()) {
            if (a.campo().equals("telefone")) {
                alteracoes.add(new CampoAlterado(a.campo(), mascararTelefone(a.valorAnterior()),
                        mascararTelefone(a.valorNovo())));
            } else {
                alteracoes.add(a);
            }
        }
        return new RegistroAuditoria(r.getId(), r.getUsuario(), r.getPerfil(), r.getDataHora(), r.getOperacao(),
                r.getEntidade(), r.getEntidadeId(), alteracoes, r.getJustificativa());
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String numeros = telefone.replaceAll("\\D", ""); // só os dígitos
        if (numeros.length() <= 4) {
            return telefone;
        }
        return "****-" + numeros.substring(numeros.length() - 4);
    }

    private static String paraTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof LocalDateTime data) {
            return data.format(FORMATO_DATA);
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
