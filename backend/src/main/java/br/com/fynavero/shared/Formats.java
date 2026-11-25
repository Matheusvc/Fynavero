package br.com.fynavero.shared;

import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatação e textos em pt-BR: moeda, nomes de mês, parâmetro de mês (AAAA-MM) e normalização de texto. */
public final class Formats {
    private Formats() {}

    public static final Locale PT_BR = Locale.of("pt", "BR");

    /** Maior valor aceito em um lançamento: R$ 1 bilhão, em centavos. */
    public static final long MAX_CENTS = 100_000_000_000L;

    /** Cores que a pessoa pode escolher (gráficos, categorias e etiquetas): tokens "chart-1" a "chart-12" da identidade visual. */
    public static final int PALETTE_SIZE = 12;

    /** A cor informada, se for da paleta; nulo/vazio volta nulo (= cor padrão). */
    public static String paletteColor(String raw) {
        String c = trimOrNull(raw);
        if (c == null) return null;
        if (!c.matches("chart-([1-9]|1[0-2])")) throw Errors.badRequest("Escolha uma cor da paleta.");
        return c;
    }

    /** Texto sem espaços nas pontas, ou nulo se ficar vazio. */
    public static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** Minúsculas e sem acentos, para buscas e comparações ("Mercado" == "mercádo"). */
    public static String fold(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
    }

    /** "R$ 1.234,56"; negativos com o sinal de menos tipográfico ("−R$ 10,00"). */
    public static String brl(long cents) {
        String s = NumberFormat.getCurrencyInstance(PT_BR).format(Math.abs(cents) / 100.0).replace(' ', ' ');
        return cents < 0 ? "−" + s : s;
    }

    /** "Novembro" */
    public static String monthName(YearMonth m) {
        return capitalize(m.getMonth().getDisplayName(TextStyle.FULL, PT_BR));
    }

    /** "novembro", para o meio de uma frase. */
    public static String monthInText(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    }

    /** "Nov" */
    public static String monthShort(YearMonth m) {
        return capitalize(m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", ""));
    }

    /** "Nov/26", usado nos eixos dos gráficos. */
    public static String monthTag(YearMonth m) {
        return monthShort(m) + "/" + String.valueOf(m.getYear()).substring(2);
    }

    public static YearMonth parseMonth(String s, ZoneId zone) {
        if (s == null || s.isBlank()) return YearMonth.now(zone);
        try {
            return YearMonth.parse(s);
        } catch (DateTimeException e) {
            throw Errors.badRequest("Mês inválido. Use o formato AAAA-MM.");
        }
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
