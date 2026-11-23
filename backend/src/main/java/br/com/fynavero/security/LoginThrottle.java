package br.com.fynavero.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bloqueia por 15 minutos após 5 tentativas de login erradas para o mesmo e-mail. */
@Component
public class LoginThrottle {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant first) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        Attempts a = attempts.get(key(email));
        return a != null && a.failures() >= MAX_FAILURES && a.first().plus(WINDOW).isAfter(Instant.now());
    }

    public void failure(String email) {
        attempts.compute(key(email), (k, a) -> {
            Instant now = Instant.now();
            if (a == null || a.first().plus(WINDOW).isBefore(now)) return new Attempts(1, now);
            return new Attempts(a.failures() + 1, a.first());
        });
    }

    public void success(String email) {
        attempts.remove(key(email));
    }

    @Scheduled(fixedRate = 3_600_000)
    void cleanup() {
        Instant limit = Instant.now().minus(WINDOW);
        attempts.values().removeIf(a -> a.first().isBefore(limit));
    }

    private static String key(String email) {
        return email == null ? "" : email.strip().toLowerCase();
    }
}
