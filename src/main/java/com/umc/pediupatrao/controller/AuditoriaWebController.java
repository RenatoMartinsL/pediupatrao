package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditoriaWebController {

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String operacao,
            @RequestParam(required = false) String usuario, Model model) {
        model.addAttribute("registros", auditoriaService.consultar(entidade, operacao, usuario));
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroOperacao", operacao);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
