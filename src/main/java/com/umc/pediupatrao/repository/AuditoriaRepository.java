package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repositório "somente inclusão": estende apenas Repository (e não MongoRepository),
 * de modo que não existem métodos de exclusão nem de alteração em lote disponíveis.
 */
public interface AuditoriaRepository extends Repository<RegistroAuditoria, String> {

    RegistroAuditoria insert(RegistroAuditoria registro);

    List<RegistroAuditoria> findAllByOrderByDataHoraDesc();

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
}
