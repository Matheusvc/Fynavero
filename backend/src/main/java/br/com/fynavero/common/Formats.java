package br.com.fynavero.common;

import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatação em pt-BR: moeda, nomes de mês e leitura do parâmetro de mês (AAAA-MM). */
public final class Formats {
    private Formats() {}

    public static final Locale PT_BR = Locale.of("pt", "BR");

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
