package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // antes era sempre "USER"; agora usa o perfil cadastrado (ADMIN, GERENTE ou ATENDENTE)
        Perfil perfil = Perfil.deTexto(usuario.getRole());
        if (perfil == null) {
            // perfil inválido: consegue logar, mas não tem permissão para nada
            return User.builder()
                    .username(usuario.getUsername())
                    .password(usuario.getPassword())
                    .authorities(List.of())
                    .build();
        }
        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(perfil.name())
                .build();
    }
}
