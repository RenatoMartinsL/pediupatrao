package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes: o ATENDENTE cadastra, altera e exclui; o GERENTE apenas consulta.
 * Toda inclusão, alteração (inclusive endereço e telefone) e exclusão é auditada.
 */
@Service
public class ClienteService {

    private static final String ENTIDADE = "Cliente";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        validar(cliente);
        normalizar(cliente);

        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(),
                    AuditoriaService.diferencas(Map.of(), retrato(salvo)), null);
            return salvo;
        }

        // Se já tem ID (cliente existente), atualiza e audita só os campos que mudaram
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        if (cliente.getEndereco() == null) {
            cliente.setEndereco(anterior.getEndereco()); // preserva o campo legado
        }
        Cliente salvo = clienteRepository.save(cliente);
        var alteracoes = AuditoriaService.diferencas(retrato(anterior), retrato(salvo));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), alteracoes, null);
        }
        return salvo;
    }

    // Método para excluir cliente
    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente anterior = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                AuditoriaService.diferencas(retrato(anterior), Map.of()), null);
    }

    private static void validar(Cliente c) {
        if (c.getNome() == null || c.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        if (c.getTelefone() == null || c.getTelefone().replaceAll("\\D", "").length() < 10) {
            throw new RegraNegocioException("Informe um telefone válido com DDD.");
        }
        if (c.getEstado() != null && !c.getEstado().isBlank() && c.getEstado().trim().length() != 2) {
            throw new RegraNegocioException("Estado deve ter 2 letras (ex.: SP).");
        }
    }

    private static void normalizar(Cliente c) {
        c.setNome(c.getNome().trim());
        c.setTelefone(c.getTelefone().trim());
        if (c.getEstado() != null) {
            c.setEstado(c.getEstado().trim().toUpperCase());
        }
    }

    /** Campos auditados do cliente. */
    private static Map<String, Object> retrato(Cliente c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", c.getNome());
        m.put("telefone", c.getTelefone());
        m.put("cep", c.getCep());
        m.put("logradouro", c.getLogradouro());
        m.put("numero", c.getNumero());
        m.put("complemento", c.getComplemento());
        m.put("bairro", c.getBairro());
        m.put("cidade", c.getCidade());
        m.put("estado", c.getEstado());
        return m;
    }
}
