package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria.CampoAlterado;
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

// Atendente cadastra, altera e exclui. Gerente só consulta.
@Service
public class ClienteService {

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

    // Salva um cliente novo (sem id) ou atualiza um existente (com id)
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        validar(cliente);
        cliente.setNome(cliente.getNome().trim());
        cliente.setTelefone(cliente.getTelefone().trim());
        if (cliente.getEstado() != null) {
            cliente.setEstado(cliente.getEstado().trim().toUpperCase());
        }

        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            List<CampoAlterado> alteracoes = AuditoriaService.diferencas(Map.of(), camposAuditados(salvo));
            auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, "Cliente", salvo.getId(), alteracoes, null);
            return salvo;
        }

        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        if (cliente.getEndereco() == null) {
            cliente.setEndereco(anterior.getEndereco()); // campo antigo do projeto base
        }
        Cliente salvo = clienteRepository.save(cliente);

        // só registra se algum campo mudou de fato
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(camposAuditados(anterior), camposAuditados(salvo));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", salvo.getId(), alteracoes, null);
        }
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente anterior = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        List<CampoAlterado> alteracoes = AuditoriaService.diferencas(camposAuditados(anterior), Map.of());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, "Cliente", id, alteracoes, null);
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

    private static Map<String, Object> camposAuditados(Cliente c) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("nome", c.getNome());
        campos.put("telefone", c.getTelefone());
        campos.put("cep", c.getCep());
        campos.put("logradouro", c.getLogradouro());
        campos.put("numero", c.getNumero());
        campos.put("complemento", c.getComplemento());
        campos.put("bairro", c.getBairro());
        campos.put("cidade", c.getCidade());
        campos.put("estado", c.getEstado());
        return campos;
    }
}
