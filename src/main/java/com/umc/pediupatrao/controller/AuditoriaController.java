package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Consulta da auditoria. Só tem GET: não existe rota para alterar ou apagar.
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoria> consultar(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String operacao,
            @RequestParam(required = false) String usuario) {
        return auditoriaService.consultar(entidade, operacao, usuario);
    }

    @GetMapping("/{entidade}/{id}")
    public List<RegistroAuditoria> historico(@PathVariable String entidade, @PathVariable String id) {
        return auditoriaService.historicoDe(entidade, id);
    }
}
