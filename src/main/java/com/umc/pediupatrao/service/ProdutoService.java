package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    public List<Produto> listarAtivos() {
        return produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .toList();
    }

    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    // Método para excluir produto
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public void excluir(String id) {
        produtoRepository.deleteById(id);
    }

    // Método para salvar um novo produto ou atualizar um produto existente
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (produto.getPreco() == null || produto.getPreco() < 0) {
            throw new RegraNegocioException("Informe um preço válido.");
        }
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }
        // Se o produto não tem ID (novo produto), salva como novo
        if (produto.getId() == null || produto.getId().isBlank()) {
            produto.setId(null);
            return produtoRepository.save(produto);
        }
        // Se já tem ID (produto existente), verifica se existe antes de atualizar
        if (!produtoRepository.existsById(produto.getId())) {
            throw new RecursoNaoEncontradoException("Produto não encontrado para atualização.");
        }
        return produtoRepository.save(produto);
    }
}
