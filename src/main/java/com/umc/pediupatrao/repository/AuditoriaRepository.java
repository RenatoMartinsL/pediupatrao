package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

// Estende Repository (e não MongoRepository) para não herdar delete nem update:
// só é possível incluir e consultar.
public interface AuditoriaRepository extends Repository<RegistroAuditoria, String> {

    RegistroAuditoria insert(RegistroAuditoria registro);

    List<RegistroAuditoria> findAllByOrderByDataHoraDesc();

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
}
