package br.com.fynavero.notification;

import br.com.fynavero.account.AccountLinks;
import br.com.fynavero.account.AccountService;
import br.com.fynavero.account.AccountToken;
import br.com.fynavero.account.AccountToken.Purpose;
import br.com.fynavero.account.Profile;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.Status;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.notification.NotificationDtos.ActionDto;
import br.com.fynavero.notification.NotificationDtos.NotificationDto;
import br.com.fynavero.notification.NotificationDtos.NotificationType;
import br.com.fynavero.notification.NotificationDtos.PreferencesDto;
import br.com.fynavero.notification.NotificationDtos.PreferencesReq;
import br.com.fynavero.planning.BudgetService;
import br.com.fynavero.planning.PlanningDtos.BudgetDto;
import br.com.fynavero.planning.PlanningDtos.BudgetLevel;
import br.com.fynavero.shared.AppProperties;
import br.com.fynavero.shared.Mailer;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static br.com.fynavero.shared.Formats.brl;
import static br.com.fynavero.shared.Formats.monthInText;

/** Central de avisos (o sino): contas vencidas, de hoje e de amanhã, e limites de categoria perto do fim. */
@Service
public class NotificationService {
    private final TransactionService transactions;
    private final CategoryService categories;
    private final BudgetService budgets;
    private final AccountService accounts;
    private final LedgerCalendar calendar;
    private final Mailer mailer;
    private final AccountLinks links;
    private final AppProperties props;

    public NotificationService(TransactionService transactions, CategoryService categories, BudgetService budgets, AccountService accounts, LedgerCalendar calendar, Mailer mailer, AccountLinks links, AppProperties props) {
        this.transactions = transactions;
        this.categories = categories;
        this.budgets = budgets;
        this.accounts = accounts;
        this.calendar = calendar;
        this.mailer = mailer;
        this.links = links;
        this.props = props;
    }

    static final int OVERDUE_DAYS = 30;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    @Transactional
    public List<NotificationDto> current(Long userId) {
        LocalDate today = calendar.today();
        Map<Long, Category> cats = categories.map(userId);
        List<NotificationDto> out = new ArrayList<>();
        for (Transaction t : unpaidBills(userId, today.minusDays(OVERDUE_DAYS), today.plusDays(1))) {
            String name = billName(t, cats);
            NotificationType type = t.date.isBefore(today) ? NotificationType.CONTA_VENCIDA
                    : t.date.equals(today) ? NotificationType.CONTA_HOJE : NotificationType.CONTA_AMANHA;
            String title = switch (type) {
                case CONTA_VENCIDA -> name + " venceu em " + DAY.format(t.date);
                case CONTA_HOJE -> name + " vence hoje";
                default -> name + " vence amanhã";
            };
            String body = brl(t.amountCents) + (type == NotificationType.CONTA_VENCIDA ? " · ainda não marcada como paga" : "");
            out.add(new NotificationDto("tx-" + t.id, type, title, body, t.date, t.id, null, t.amountCents));
        }
        YearMonth month = YearMonth.from(today);
        for (BudgetDto b : budgets.status(userId, month)) {
            if (b.level() == BudgetLevel.OK) continue;
            out.add(limitNotification(b, month));
        }
        return out;
    }

    /** Despesas previstas (ainda não pagas) entre as datas, da mais antiga para a mais nova. */
    List<Transaction> unpaidBills(Long userId, LocalDate from, LocalDate to) {
        return transactions.range(userId, from, to).stream()
                .filter(t -> t.kind == Kind.DESPESA && t.status == Status.PREVISTO)
                .sorted(Comparator.comparing((Transaction t) -> t.date).thenComparing(t -> t.id))
                .toList();
    }

    static NotificationDto limitNotification(BudgetDto b, YearMonth month) {
        boolean over = b.level() == BudgetLevel.ESTOURADO;
        String title = over ? b.name() + " passou do limite" : b.name() + ": " + b.pctBps() / 100 + "% do limite";
        String body = brl(b.spentCents()) + " de " + brl(b.limitCents()) + " em " + monthInText(month)
                + (over ? " (" + b.pctBps() / 100 + "%)" : "");
        return new NotificationDto("limit-" + b.categoryId(), over ? NotificationType.LIMITE_ESTOURADO : NotificationType.LIMITE_ALERTA,
                title, body, null, null, b.categoryId(), b.spentCents());
    }

    static String billName(Transaction t, Map<Long, Category> cats) {
        if (t.description != null && !t.description.isBlank()) return t.description;
        Category c = cats.get(t.categoryId);
        return c != null ? c.name : "Conta";
    }

    public PreferencesDto preferences(Long userId) {
        Profile p = accounts.require(userId);
        return new PreferencesDto(p.notifyBills, p.notifyLimits, p.notifySummary, mailer.configured());
    }

    @Transactional
    public PreferencesDto updatePreferences(Long userId, PreferencesReq req) {
        Profile p = accounts.require(userId);
        p.notifyBills = req.bills();
        p.notifyLimits = req.limits();
        p.notifySummary = req.summary();
        return new PreferencesDto(p.notifyBills, p.notifyLimits, p.notifySummary, mailer.configured());
    }

    // ================================================================ "Marcar como paga" direto do e-mail
    private static final String INVALID = "Este link não vale mais. Abra a Fynavero para marcar a conta como paga.";

    /** Cria o link de "Marcar como paga" para uma conta. */
    public String payLink(Transaction t) {
        Instant until = t.date.plusDays(8).atStartOfDay(props.zoneId()).toInstant();
        String token = links.issue(t.userId, Purpose.PAY_BILL, Duration.between(Instant.now(), until), t.id);
        return props.baseUrl() + "/conta-paga?token=" + token;
    }

    @Transactional(readOnly = true)
    public ActionDto describe(String token) {
        AccountToken a = links.peek(token, Purpose.PAY_BILL, INVALID);
        Transaction t = transactions.require(a.userId, a.refId);
        return toDto(t, t.status == Status.REALIZADO);
    }

    @Transactional
    public ActionDto pay(String token) {
        AccountToken a = links.peek(token, Purpose.PAY_BILL, INVALID);
        Transaction t = transactions.markPaid(a.userId, a.refId);
        a.usedAt = Instant.now();
        return toDto(t, true);
    }

    private ActionDto toDto(Transaction t, boolean paid) {
        return new ActionDto(NotificationService.billName(t, categories.map(t.userId)), t.amountCents, t.date, paid);
    }
}
