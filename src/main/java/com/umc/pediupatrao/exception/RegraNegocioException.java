package com.umc.pediupatrao.exception;

// Erro de regra de negócio (ex.: desconto acima de 20%, status fora de ordem)
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
