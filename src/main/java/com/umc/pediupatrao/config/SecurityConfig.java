package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Autorização por perfil em duas camadas:
 * 1) regras de URL abaixo (rotas de tela e APIs);
 * 2) @PreAuthorize nos serviços (@EnableMethodSecurity), que protege a regra mesmo se uma rota nova for criada.
 * CSRF fica ATIVO: formulários Thymeleaf recebem o token automaticamente e as chamadas fetch
 * enviam o cabeçalho X-CSRF-TOKEN (ver layout.html).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Token CSRF disponível já na renderização da página (necessário para as meta tags do layout)
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/acesso-negado", "/error", "/css/**", "/js/**", "/images/**",
                                "/favicon.ico").permitAll()

                        // Usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)

                        // Auditoria: somente ADMIN e GERENTE (e apenas leitura: não há rotas de escrita)
                        .requestMatchers(HttpMethod.GET, "/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // Desconto e cancelamento: exclusivos do GERENTE (nem o ADMIN pode)
                        .requestMatchers("/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole(GERENTE)

                        // Pedidos: ADMIN consulta; GERENTE e ATENDENTE registram e acompanham
                        .requestMatchers("/pedidos/novo").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*", "/api/pedidos", "/api/pedidos/*")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)

                        // Clientes: GERENTE consulta; ATENDENTE cadastra, altera e exclui
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*")
                                .hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // Produtos: todos consultam; ADMIN e GERENTE mantêm o cardápio
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos", "/api/produtos/*")
                                .hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        .requestMatchers("/").authenticated()
                        // Qualquer outra rota (ex.: páginas de exemplo do projeto base) fica bloqueada
                        .anyRequest().hasRole(ADMIN)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .headers(headers -> headers
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    /** APIs respondem 401 em JSON; telas redirecionam para o login. */
    private AuthenticationEntryPoint entryPoint() {
        LoginUrlAuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint("/login");
        return (request, response, authException) -> {
            if (isApi(request)) {
                escreverJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Autenticação necessária.");
            } else {
                login.commence(request, response, authException);
            }
        };
    }

    /** APIs respondem 403 em JSON; telas mostram a página de acesso negado. */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (isApi(request)) {
                escreverJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "Acesso negado: seu perfil não permite esta operação.");
            } else {
                response.sendRedirect("/acesso-negado");
            }
        };
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private static void escreverJson(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"erro\":\"" + mensagem + "\"}");
    }
}
