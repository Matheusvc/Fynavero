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
 * Limite de pedidos por chave (IP ou e-mail) numa janela de tempo: segura robôs criando contas em massa ou
 * disparando e-mails. Fica na memória do servidor — suficiente para uma instância; com várias, usar Redis.
 */
@Component
public class RateLimiter {
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Registra o pedido e lança 429 se a chave já passou de {@code max} pedidos na janela. */
    public void check(String key, int max, Duration window, String message) {
        if (!tryAcquire(key, max, window)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public boolean tryAcquire(String key, int max, Duration window) {
        Instant now = Instant.now();
        Instant limit = now.minus(window);
        boolean[] allowed = {false};
        hits.compute(key, (k, q) -> {
            Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
            while (!d.isEmpty() && d.peekFirst().isBefore(limit)) d.pollFirst();
            if (d.size() < max) {
                d.addLast(now);
                allowed[0] = true;
            }
            return d;
        });
        return allowed[0];
    }

    @Scheduled(fixedRate = 3_600_000)
    void cleanup() {
        Instant limit = Instant.now().minus(Duration.ofDays(1));
        hits.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(limit));
    }
}
