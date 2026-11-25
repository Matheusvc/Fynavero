package br.com.fynavero.notification;

import br.com.fynavero.notification.NotificationLog.NotificationLogRepository;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.account.Profile;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.shared.Mailer;
import br.com.fynavero.shared.AppProperties;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.overview.MonthSummaryService;
import br.com.fynavero.overview.MonthSummaryService.MonthSummary;
import br.com.fynavero.planning.BudgetService;
import br.com.fynavero.planning.PlanningDtos.BudgetDto;
import br.com.fynavero.planning.PlanningDtos.BudgetLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.shared.Mailer.button;
import static br.com.fynavero.shared.Mailer.esc;
import static br.com.fynavero.shared.Mailer.page;
import static br.com.fynavero.shared.Formats.brl;
import static br.com.fynavero.shared.Formats.monthInText;

/**
 * E-mails de aviso: lembrete de contas que vencem amanhã, limites de categoria e resumo do mês.
 * Cada aviso é registrado em notification_log e nunca sai duas vezes.
 */
@Service
public class NotificationMailer {
    static final String KIND_BILL = "CONTA";
    static final String KIND_LIMIT = "LIMITE";
    static final String KIND_SUMMARY = "RESUMO";

    private final AccountService accounts;
    private final NotificationService notifications;
    private final BudgetService budgets;
    private final MonthSummaryService summaries;
    private final CategoryService categories;
    private final ActionService actions;
    private final NotificationLogRepository log;
    private final LedgerCalendar calendar;
    private final Mailer mailer;
    private final AppProperties props;

    public NotificationMailer(AccountService accounts, NotificationService notifications, BudgetService budgets,
                              MonthSummaryService summaries, CategoryService categories, ActionService actions,
                              NotificationLogRepository log, LedgerCalendar calendar, Mailer mailer,
                              AppProperties props) {
        this.accounts = accounts;
        this.notifications = notifications;
        this.budgets = budgets;
        this.summaries = summaries;
        this.categories = categories;
        this.actions = actions;
        this.log = log;
        this.calendar = calendar;
        this.mailer = mailer;
        this.props = props;
    }

    // ------------------------------------------------------------------ contas que vencem amanhã

    /** Envia um e-mail com as contas de amanhã ainda não avisadas; devolve quantas entraram no e-mail. */
    @Transactional
    public int sendBillReminders(Long userId) {
        Profile p = accounts.require(userId);
        LocalDate tomorrow = calendar.today().plusDays(1);
        List<Transaction> bills = notifications.unpaidBills(userId, tomorrow, tomorrow).stream()
                .filter(t -> !log.existsByUserIdAndKindAndRef(userId, KIND_BILL, billRef(t)))
                .toList();
        if (bills.isEmpty()) return 0;

        Map<Long, Category> cats = categories.map(userId);
        long total = bills.stream().mapToLong(t -> t.amountCents).sum();
        String subject = bills.size() == 1
                ? "Amanhã vence: " + NotificationService.billName(bills.getFirst(), cats) + " (" + brl(total) + ")"
                : "Amanhã vencem " + bills.size() + " contas (" + brl(total) + ")";

        StringBuilder text = new StringBuilder("Olá, " + p.firstName() + "!\n\nEstas contas vencem amanhã:\n\n");
        StringBuilder rows = new StringBuilder();
        for (Transaction t : bills) {
            String name = NotificationService.billName(t, cats);
            String link = actions.payLink(t);
            text.append("- ").append(name).append(": ").append(brl(t.amountCents))
                    .append("\n  Marcar como paga: ").append(link).append("\n");
            rows.append("<tr><td style=\"padding:12px 0;border-bottom:1px solid #eeeeee\"><strong>").append(esc(name))
                    .append("</strong><br><span style=\"color:#646464\">").append(esc(brl(t.amountCents)))
                    .append(installmentLabel(t)).append("</span></td><td align=\"right\" style=\"padding:12px 0;border-bottom:1px solid #eeeeee\">")
                    .append(button("Marcar como paga", link)).append("</td></tr>");
        }
        text.append("\nVer tudo na Fynavero: ").append(props.baseUrl()).append("\n");
        String html = page(bills.size() == 1 ? "Uma conta vence amanhã" : bills.size() + " contas vencem amanhã",
                "<p>Olá, " + esc(p.firstName()) + "! Para não esquecer:</p>"
                        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + rows + "</table>"
                        + "<p style=\"margin-top:16px\">Total: <strong>" + esc(brl(total)) + "</strong></p>",
                settingsUrl());
        if (!mailer.send(p.email, subject, text.toString(), html)) return 0;
        bills.forEach(t -> record(userId, KIND_BILL, billRef(t)));
        return bills.size();
    }

    private static String billRef(Transaction t) {
        return t.id + ":" + t.date;
    }

    private static String installmentLabel(Transaction t) {
        return t.installmentNumber != null && t.installmentTotal != null
                ? " · parcela " + t.installmentNumber + "/" + t.installmentTotal : "";
    }

    // ------------------------------------------------------------------ limites de categoria

    /** Avisa os limites que passaram de 80% ou de 100% neste mês e ainda não foram avisados. */
    @Transactional
    public int sendLimitAlerts(Long userId) {
        Profile p = accounts.require(userId);
        YearMonth month = calendar.currentMonth();
        List<BudgetDto> fresh = new ArrayList<>();
        for (BudgetDto b : budgets.status(userId, month)) {
            if (b.level() == BudgetLevel.OK) continue;
            if (!log.existsByUserIdAndKindAndRef(userId, KIND_LIMIT, limitRef(b, month, b.level()))) fresh.add(b);
        }
        if (fresh.isEmpty()) return 0;

        String subject = fresh.size() == 1
                ? NotificationService.limitNotification(fresh.getFirst(), month).title()
                : fresh.size() + " categorias perto do limite em " + monthInText(month);
        StringBuilder text = new StringBuilder("Olá, " + p.firstName() + "!\n\n");
        StringBuilder rows = new StringBuilder();
        for (BudgetDto b : fresh) {
            var n = NotificationService.limitNotification(b, month);
            text.append("- ").append(n.title()).append(" — ").append(n.body()).append("\n");
            int width = Math.min(100, b.pctBps() / 100);
            String color = b.level() == BudgetLevel.ESTOURADO ? "#C62828" : "#E85002";
            rows.append("<p style=\"margin:16px 0 4px\"><strong>").append(esc(n.title())).append("</strong><br>")
                    .append("<span style=\"color:#646464\">").append(esc(n.body())).append("</span></p>")
                    .append("<div style=\"background:#eeeeee;border-radius:4px;height:8px\"><div style=\"background:")
                    .append(color).append(";width:").append(width).append("%;height:8px;border-radius:4px\"></div></div>");
        }
        text.append("\nVer na Fynavero: ").append(props.baseUrl()).append("\n");
        String html = page("Seus limites de " + monthInText(month), "<p>Olá, " + esc(p.firstName())
                + "! Algumas categorias estão chegando ao limite que você definiu:</p>" + rows
                + "<p style=\"margin-top:20px\">" + button("Ver meus gastos", props.baseUrl()) + "</p>", settingsUrl());
        if (!mailer.send(p.email, subject, text.toString(), html)) return 0;
        for (BudgetDto b : fresh) {
            record(userId, KIND_LIMIT, limitRef(b, month, BudgetLevel.ALERTA));
            if (b.level() == BudgetLevel.ESTOURADO) record(userId, KIND_LIMIT, limitRef(b, month, BudgetLevel.ESTOURADO));
        }
        return fresh.size();
    }

    private static String limitRef(BudgetDto b, YearMonth m, BudgetLevel level) {
        return b.categoryId() + ":" + m + ":" + (level == BudgetLevel.ESTOURADO ? 100 : 80);
    }

    // ------------------------------------------------------------------ resumo do mês

    /**
     * Resumo do mês fechado. Sem {@code force}, só envia uma vez por mês e só se houve lançamento;
     * com {@code force} (botão "Enviar um exemplo"), sempre envia.
     */
    @Transactional
    public boolean sendMonthlySummary(Long userId, YearMonth m, boolean force) {
        Profile p = accounts.require(userId);
        if (!force && log.existsByUserIdAndKindAndRef(userId, KIND_SUMMARY, m.toString())) return false;
        MonthSummary s = summaries.summary(userId, m);
        if (!force && s.count() == 0) return false;

        String month = monthInText(m);
        String result = s.saldo() >= 0 ? "Sobrou " + brl(s.saldo()) : "Faltou " + brl(-s.saldo());
        String top = s.topCategory() == null ? "Nenhuma despesa lançada"
                : s.topCategory() + " (" + brl(s.topCategoryCents()) + ")";
        String text = "Olá, " + p.firstName() + "! Este é o resumo de " + month + ":\n\n"
                + "Entrou: " + brl(s.receitas()) + "\n"
                + "Saiu: " + brl(s.despesas()) + "\n"
                + "Investido: " + brl(s.investido()) + "\n"
                + result + "\n"
                + "Maior categoria de gasto: " + top + "\n\n"
                + "Ver o relatório completo: " + props.baseUrl() + "/relatorios\n";
        String html = page("Seu resumo de " + month,
                "<p>Olá, " + esc(p.firstName()) + "! Veja como foi " + esc(month) + ":</p>"
                        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"font-size:15px\">"
                        + row("Entrou", brl(s.receitas()), compare(s.receitas(), s.previous().receitas()))
                        + row("Saiu", brl(s.despesas()), compare(s.despesas(), s.previous().despesas()))
                        + row("Investido", brl(s.investido()), null)
                        + "<tr><td style=\"padding:14px 0 6px;font-weight:600\">" + (s.saldo() >= 0 ? "Sobrou" : "Faltou")
                        + "</td><td align=\"right\" style=\"padding:14px 0 6px;font-weight:700;font-size:20px;color:"
                        + (s.saldo() >= 0 ? "#000000" : "#C62828") + "\">" + esc(brl(Math.abs(s.saldo()))) + "</td></tr>"
                        + "</table>"
                        + "<p style=\"margin-top:16px\">Maior categoria de gasto: <strong>" + esc(top) + "</strong></p>"
                        + "<p style=\"margin-top:20px\">" + button("Ver relatório completo", props.baseUrl() + "/relatorios") + "</p>",
                settingsUrl());
        String subject = "Seu resumo de " + month + ": " + (s.saldo() >= 0 ? "sobrou " : "faltou ") + brl(Math.abs(s.saldo()));
        if (!mailer.send(p.email, subject, text, html)) return false;
        if (!force) record(userId, KIND_SUMMARY, m.toString());
        return true;
    }

    private static String row(String label, String value, String note) {
        return "<tr><td style=\"padding:6px 0;border-bottom:1px solid #eeeeee\">" + esc(label)
                + (note == null ? "" : "<br><span style=\"color:#646464;font-size:12px\">" + esc(note) + "</span>")
                + "</td><td align=\"right\" style=\"padding:6px 0;border-bottom:1px solid #eeeeee;font-weight:600\">"
                + esc(value) + "</td></tr>";
    }

    private static String compare(long current, long previous) {
        if (previous == 0) return null;
        long diff = current - previous;
        if (diff == 0) return "igual ao mês anterior";
        return (diff > 0 ? brl(diff) + " a mais" : brl(-diff) + " a menos") + " que no mês anterior";
    }

    // ------------------------------------------------------------------ apoio

    private void record(Long userId, String kind, String ref) {
        if (log.existsByUserIdAndKindAndRef(userId, kind, ref)) return;
        NotificationLog n = new NotificationLog();
        n.userId = userId;
        n.kind = kind;
        n.ref = ref;
        n.sentAt = Instant.now();
        log.save(n);
    }

    private String settingsUrl() {
        return props.baseUrl() + "/configuracoes#notificacoes";
    }
}
