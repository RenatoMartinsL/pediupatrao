package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
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
        return usuarioRepository.findByUsername(username)
                .map(usuario -> {
                    Perfil perfil = Perfil.deTexto(usuario.getRole());
                    User.UserBuilder builder = User.builder()
                            .username(usuario.getUsername())
                            .password(usuario.getPassword());
                    // Atribui a role do perfil cadastrado (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE).
                    // Usuário sem perfil válido autentica, mas não recebe nenhuma permissão.
                    return perfil != null
                            ? builder.roles(perfil.name()).build()
                            : builder.authorities(java.util.List.of()).build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
