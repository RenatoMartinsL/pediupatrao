package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

// Registro de auditoria. Os campos são final e não há setters: depois de gravado, não muda mais.
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;
    private final String usuario;
    private final String perfil;
    private final LocalDateTime dataHora;
    private final TipoOperacao operacao;
    private final String entidade;     // Pedido, Cliente ou Usuario
    private final String entidadeId;
    private final List<CampoAlterado> alteracoes;
    private final String justificativa;

    @PersistenceCreator // construtor que o Spring usa ao ler do MongoDB
    public RegistroAuditoria(String id, String usuario, String perfil, LocalDateTime dataHora,
            TipoOperacao operacao, String entidade, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        this.id = id;
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = dataHora;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        this.justificativa = justificativa;
    }

    public String getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public LocalDateTime getDataHora() { return dataHora; }
    public TipoOperacao getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<CampoAlterado> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }

    // Um campo que mudou: nome, valor antigo e valor novo
    public record CampoAlterado(String campo, String valorAnterior, String valorNovo) {
    }
}
