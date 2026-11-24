package br.com.fynavero.common;

import org.springframework.web.util.HtmlUtils;

/** Moldura HTML dos e-mails com a identidade Fynavero (estilos inline, que é o que os leitores de e-mail aceitam). */
public final class EmailLayout {
    private EmailLayout() {}

    public static final String ORANGE = "#E85002";
    public static final String DARK = "#333333";
    public static final String GRAY = "#646464";
    public static final String LIGHT = "#F9F9F9";

    public static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    /** Página completa: logo, título, conteúdo (já em HTML) e rodapé com o link das preferências. */
    public static String page(String title, String bodyHtml, String settingsUrl) {
        return """
            <!doctype html><html lang="pt-BR"><body style="margin:0;background:%s;font-family:Montserrat,Arial,sans-serif;color:%s">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:12px">
            <tr><td style="padding:24px 24px 8px;font-weight:600;letter-spacing:.12em;font-size:18px;color:#000000">FYNAVERO<sup style="color:%s;font-size:10px">®</sup></td></tr>
            <tr><td style="padding:8px 24px 0"><h1 style="margin:0;font-size:20px;color:#000000">%s</h1></td></tr>
            <tr><td style="padding:16px 24px 24px;font-size:15px;line-height:1.5">%s</td></tr>
            </table>
            <p style="max-width:560px;font-size:12px;color:%s;padding:12px 24px">Você recebe este e-mail porque tem uma conta na Fynavero.
            <a href="%s" style="color:%s">Escolher quais avisos receber</a>.</p>
            </td></tr></table></body></html>"""
                .formatted(LIGHT, DARK, ORANGE, esc(title), bodyHtml, GRAY, esc(settingsUrl), ORANGE);
    }

    public static String button(String label, String url) {
        return "<a href=\"" + esc(url) + "\" style=\"display:inline-block;background:" + ORANGE
                + ";color:#ffffff;text-decoration:none;font-weight:600;padding:10px 16px;border-radius:8px\">" + esc(label) + "</a>";
    }
}
