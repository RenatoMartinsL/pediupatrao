package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria. É imutável: não possui setters e o repositório
 * não expõe operações de alteração ou exclusão.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;
    @Indexed
    private final String usuario;
    private final String perfil;
    @Indexed
    private final LocalDateTime dataHora;
    private final TipoOperacao operacao;
    @Indexed
    private final String entidade;
    @Indexed
    private final String entidadeId;
    private final List<CampoAlterado> alteracoes;
    private final String justificativa;

    @PersistenceCreator
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

    /** Novo registro (o id é gerado pelo MongoDB). */
    public static RegistroAuditoria novo(String usuario, String perfil, LocalDateTime dataHora,
            TipoOperacao operacao, String entidade, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        return new RegistroAuditoria(null, usuario, perfil, dataHora, operacao, entidade,
                entidadeId, alteracoes, justificativa);
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

    /** Campo modificado com seus valores anterior e novo. */
    public record CampoAlterado(String campo, String valorAnterior, String valorNovo) {
    }
}
