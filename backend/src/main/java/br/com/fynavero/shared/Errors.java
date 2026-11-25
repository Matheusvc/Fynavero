package br.com.fynavero.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Erros de regra de negócio com mensagem em português, convertidos em {"message": "..."} pelo {@link ApiExceptionHandler}. */
public final class Errors {
    private Errors() {}

    public static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Não encontrado.");
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar.");
    }
}
