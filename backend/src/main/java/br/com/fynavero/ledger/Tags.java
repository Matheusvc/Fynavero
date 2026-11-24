package br.com.fynavero.ledger;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Etiquetas dos lançamentos (#viagem, #reforma). Guardadas em minúsculas, sem "#", separadas por vírgula.
 * Também aceitamos etiquetas digitadas na descrição ("Hotel #viagem").
 */
public final class Tags {
    private Tags() {}

    public static final int MAX_TAGS = 10;
    public static final int MAX_LENGTH = 30;
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_-]+)");

    /** Tira as #etiquetas do texto ("Hotel #viagem" → "Hotel"); elas passam a viver só nas etiquetas. */
    public static String stripHashtags(String text) {
        if (text == null) return null;
        return HASHTAG.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ").strip();
    }

    /** O mesmo pedido de lançamento, com a descrição sem as #etiquetas. */
    public static LedgerDtos.TxReq withoutHashtags(LedgerDtos.TxReq r) {
        return new LedgerDtos.TxReq(r.kind(), r.amountCents(), r.categoryId(), r.date(), stripHashtags(r.description()),
                r.paymentMethod(), r.note(), r.repeatMonthly(), r.installments(), r.installmentValue(), r.loan(), r.tags());
    }

    /** Normaliza uma etiqueta: sem "#", minúscula, espaços viram "-", só letras, números, "-" e "_". */
    public static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.strip().replaceFirst("^#+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", "-")
                .replaceAll("[^\\p{L}\\p{N}_-]", "");
        if (s.isEmpty()) return null;
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }

    /** Junta as etiquetas informadas com as #etiquetas da descrição; devolve null quando não há nenhuma. */
    public static String toCsv(Collection<String> tags, String description) {
        Set<String> out = new LinkedHashSet<>();
        if (tags != null) tags.stream().map(Tags::clean).filter(t -> t != null).forEach(out::add);
        if (description != null) {
            Matcher m = HASHTAG.matcher(description);
            while (m.find()) {
                String t = clean(m.group(1));
                if (t != null) out.add(t);
            }
        }
        return out.isEmpty() ? null : String.join(",", out.stream().limit(MAX_TAGS).toList());
    }

    public static List<String> list(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean has(String csv, String tag) {
        String t = clean(tag);
        return t != null && list(csv).contains(t);
    }
}
