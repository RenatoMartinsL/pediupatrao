package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

// Cria um usuário de cada perfil e um cardápio de exemplo na primeira execução
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos, PasswordEncoder encoder) {
        return args -> {
            criarUsuario(usuarios, encoder, "admin", "teste123", Perfil.ADMIN);
            criarUsuario(usuarios, encoder, "gerente01", "teste123", Perfil.GERENTE);
            criarUsuario(usuarios, encoder, "atendente01", "teste123", Perfil.ATENDENTE);

            // Usuários antigos sem perfil válido (ex.: role "USER" do projeto base) passam a ATENDENTE,
            // exceto o admin original, que volta a ser ADMIN.
            usuarios.findAll().stream()
                    .filter(u -> Perfil.deTexto(u.getRole()) == null)
                    .forEach(u -> {
                        u.setRole("admin".equals(u.getUsername()) ? Perfil.ADMIN.name() : Perfil.ATENDENTE.name());
                        usuarios.save(u);
                        log.info("Perfil do usuário {} ajustado para {}", u.getUsername(), u.getRole());
                    });

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa (grande)", "Pizza", 52.90));
                produtos.save(produto("Pizza Mussarela (grande)", "Pizza", 49.90));
                produtos.save(produto("Pizza Portuguesa (grande)", "Pizza", 58.90));
                produtos.save(produto("Pizza Frango com Catupiry (grande)", "Pizza", 59.90));
                produtos.save(produto("Refrigerante 2L", "Bebida", 14.00));
                produtos.save(produto("Borda recheada", "Adicional", 9.00));
                log.info("Cardápio inicial criado.");
            }
        };
    }

    private static void criarUsuario(UsuarioRepository repo, PasswordEncoder encoder,
            String username, String senha, Perfil perfil) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(senha));
            user.setRole(perfil.name());
            repo.save(user);
            log.info("Usuário {} ({}) criado.", username, perfil);
        }
    }

    private static Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }
}
