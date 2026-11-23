package br.com.fynavero.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Map;

public final class WebSupport {
    private WebSupport() {}

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("name", "nome"), Map.entry("email", "e-mail"), Map.entry("password", "senha"),
            Map.entry("next", "nova senha"), Map.entry("amountCents", "valor"), Map.entry("categoryId", "categoria"),
            Map.entry("date", "data"), Map.entry("description", "descrição"), Map.entry("day", "dia"),
            Map.entry("installments", "número de parcelas"), Map.entry("bps", "percentual"),
            Map.entry("goalCents", "meta"), Map.entry("kind", "tipo"), Map.entry("note", "observação"));

    /** Erros sempre como {"message": "..."} em português, sem detalhes internos. */
    @RestControllerAdvice
    public static class ApiErrors {
        private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

        private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
            return ResponseEntity.status(status).body(Map.of("message", message));
        }

        @ExceptionHandler(ResponseStatusException.class)
        ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
            HttpStatus s = HttpStatus.valueOf(e.getStatusCode().value());
            return body(s, e.getReason() != null ? e.getReason() : "Não foi possível concluir.");
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
            var fe = e.getBindingResult().getFieldError();
            if (fe == null) return body(HttpStatus.BAD_REQUEST, "Confira os dados informados.");
            if ("acceptTerms".equals(fe.getField())) return body(HttpStatus.BAD_REQUEST, fe.getDefaultMessage());
            String field = fe.getField().replaceAll(".*\\.", "").replaceAll("\\[\\d+]", "");
            String label = FIELD_LABELS.getOrDefault(field, field);
            if ("password".equals(field) || "next".equals(field)) return body(HttpStatus.BAD_REQUEST, "A senha precisa ter pelo menos 8 caracteres.");
            if ("email".equals(field)) return body(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.");
            return body(HttpStatus.BAD_REQUEST, "Confira o campo " + label + ".");
        }

        @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
        ResponseEntity<Map<String, String>> unreadable(Exception e) {
            return body(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        }

        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
            return body(HttpStatus.FORBIDDEN, "Sessão expirada. Recarregue a página.");
        }

        @ExceptionHandler(NoResourceFoundException.class)
        ResponseEntity<Map<String, String>> noResource(NoResourceFoundException e) {
            return body(HttpStatus.NOT_FOUND, "Não encontrado.");
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<Map<String, String>> other(Exception e) {
            log.error("Erro inesperado", e);
            return body(HttpStatus.INTERNAL_SERVER_ERROR, "Algo deu errado. Tente de novo em instantes.");
        }
    }

    /** Serve o front-end embutido e devolve index.html para as rotas do app (ex.: /lancamentos). */
    @Configuration
    public static class SpaConfig implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/")
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String path, Resource location) throws IOException {
                            Resource r = location.createRelative(path);
                            if (r.exists() && r.isReadable()) return r;
                            if (path.startsWith("api/") || path.contains(".")) return null;
                            Resource index = location.createRelative("index.html");
                            return index.exists() ? index : null;
                        }
                    });
        }
    }
}
