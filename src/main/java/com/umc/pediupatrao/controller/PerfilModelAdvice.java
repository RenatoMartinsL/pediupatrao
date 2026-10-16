package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Disponibiliza o perfil do usuário logado para todas as telas (exibido no menu lateral). */
@ControllerAdvice(annotations = Controller.class)
public class PerfilModelAdvice {

    @ModelAttribute("perfilLogado")
    public String perfilLogado() {
        return UsuarioLogado.perfil();
    }
}
