package br.com.fynavero.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {
    private Texts() {}

    public static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** Minúsculas e sem acentos, para buscas e comparações ("Mercado" == "mercádo"). */
    public static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
}
