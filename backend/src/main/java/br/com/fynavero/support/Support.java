package br.com.fynavero.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;

public final class Support {
    private Support() {}

    public static final Locale PT_BR = Locale.of("pt", "BR");

    @ConfigurationProperties("app")
    public record AppProperties(String baseUrl, String zone, boolean demoData, String mailFrom) {
        public ZoneId zoneId() { return ZoneId.of(zone == null ? "America/Sao_Paulo" : zone); }
    }

    public static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Não encontrado.");
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static String brl(long cents) {
        NumberFormat f = NumberFormat.getCurrencyInstance(PT_BR);
        String s = f.format(Math.abs(cents) / 100.0).replace(' ', ' ');
        return cents < 0 ? "−" + s : s;
    }

    public static String monthName(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static String monthShort(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static YearMonth parseMonth(String s, ZoneId zone) {
        if (s == null || s.isBlank()) return YearMonth.now(zone);
        try {
            return YearMonth.parse(s);
        } catch (DateTimeException e) {
            throw badRequest("Mês inválido. Use o formato AAAA-MM.");
        }
    }

    public static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
