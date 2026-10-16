package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados que o cliente HTTP pode enviar para registrar/alterar um pedido.
 * Não há campos de preço, desconto ou total: esses valores são sempre calculados no servidor.
 */
public class PedidoForm {

    private String clienteId;
    private List<Item> itens = new ArrayList<>();
    private String observacao;

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<Item> getItens() { return itens; }
    public void setItens(List<Item> itens) { this.itens = itens; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public static class Item {
        private String produtoId;
        private Integer quantidade;

        public Item() {
        }

        public Item(String produtoId, Integer quantidade) {
            this.produtoId = produtoId;
            this.quantidade = quantidade;
        }

        public String getProdutoId() { return produtoId; }
        public void setProdutoId(String produtoId) { this.produtoId = produtoId; }

        public Integer getQuantidade() { return quantidade; }
        public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
    }
}
