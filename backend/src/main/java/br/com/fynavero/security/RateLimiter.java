package br.com.fynavero.security;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de eventos por chave (IP, e-mail, conta) numa janela de tempo: cadastros, pedidos de link e senhas
 * erradas. Fica na memória do servidor — suficiente para uma instância; com várias, usar Redis.
 */
@Component
public class RateLimiter {
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Registra o pedido e lança 429 se a chave já passou de {@code max} pedidos na janela. */
    public void check(String key, int max, Duration window, String message) {
        boolean[] allowed = {false};
        hits.compute(key, (k, q) -> {
            Deque<Instant> d = prune(q, window);
            if (d.size() < max) {
                d.addLast(Instant.now());
                allowed[0] = true;
            }
            return d;
        });
        if (!allowed[0]) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /** Já houve {@code max} eventos na janela? (não registra nada) */
    public boolean isLimited(String key, int max, Duration window) {
        Deque<Instant> d = hits.computeIfPresent(key, (k, q) -> prune(q, window));
        return d != null && d.size() >= max;
    }

    /** Registra um evento (ex.: senha errada). */
    public void hit(String key) {
        hits.compute(key, (k, q) -> {
            Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
            d.addLast(Instant.now());
            return d;
        });
    }

    /** Zera a contagem (ex.: login certo depois de erros). */
    public void reset(String key) {
        hits.remove(key);
    }

    private static Deque<Instant> prune(Deque<Instant> q, Duration window) {
        Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
        Instant limit = Instant.now().minus(window);
        while (!d.isEmpty() && d.peekFirst().isBefore(limit)) d.pollFirst();
        return d;
    }

    @Scheduled(fixedRate = 3_600_000)
    void cleanup() {
        Instant limit = Instant.now().minus(Duration.ofDays(1));
        hits.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(limit));
    }
}
