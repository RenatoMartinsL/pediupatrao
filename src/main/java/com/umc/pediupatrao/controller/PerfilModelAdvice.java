package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Deixa o perfil do usuário logado disponível em todas as telas (aparece no menu)
@ControllerAdvice(annotations = Controller.class)
public class PerfilModelAdvice {

    @ModelAttribute("perfilLogado")
    public String perfilLogado() {
        return UsuarioLogado.perfil();
    }
}
