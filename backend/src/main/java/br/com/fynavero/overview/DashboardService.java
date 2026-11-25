package br.com.fynavero.overview;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.account.Profile;
import br.com.fynavero.ledger.FinanceCalculator;
import br.com.fynavero.ledger.FinanceCalculator.Contribution;
import br.com.fynavero.ledger.FinanceCalculator.MonthTotals;
import br.com.fynavero.ledger.FinanceCalculator.Slice;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Kind;
import br.com.fynavero.ledger.Status;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionRepository;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.overview.OverviewDtos.DashboardDto;
import br.com.fynavero.overview.OverviewDtos.MonthBarDto;
import br.com.fynavero.overview.OverviewDtos.SliceDto;
import br.com.fynavero.overview.OverviewDtos.TotalsDto;
import br.com.fynavero.overview.OverviewDtos.UpcomingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.fynavero.shared.Formats.monthTag;
import static br.com.fynavero.overview.LedgerFigures.byMonth;
import static br.com.fynavero.overview.LedgerFigures.entries;

/** Tela Início: saldo para passar o mês, totais, "Para onde foi seu dinheiro", 6 meses, vencimentos e dicas. */
@Service
public class DashboardService {
    private static final int TOP_CATEGORIES = 6;
    private static final int UPCOMING_DAYS = 15;
    private static final int UPCOMING_LIMIT = 8;

    private final TransactionService transactionService;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final AccountService accounts;
    private final LedgerCalendar calendar;
    private final InsightWriter insights;

    public DashboardService(TransactionService transactionService, TransactionRepository transactions,
                            CategoryService categories, AccountService accounts, LedgerCalendar calendar,
                            InsightWriter insights) {
        this.transactionService = transactionService;
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
        this.calendar = calendar;
        this.insights = insights;
    }

    @Transactional
    public DashboardDto dashboard(Long userId, YearMonth m) {
        Profile profile = accounts.require(userId);
        Contribution c = accounts.contributionOf(userId);
        Map<Long, Category> cats = categories.map(userId);
        Set<Long> loans = CategoryService.loanIds(cats.values());
        Map<YearMonth, List<Transaction>> months = byMonth(transactionService.range(userId, m.minusMonths(5).atDay(1), m.atEndOfMonth()));

        List<Transaction> cur = months.getOrDefault(m, List.of());
        List<Transaction> prev = months.getOrDefault(m.minusMonths(1), List.of());
        MonthTotals current = FinanceCalculator.totals(entries(cur, loans), c);
        MonthTotals previous = FinanceCalculator.totals(entries(prev, loans), c);
        Map<Long, Long> curByCat = FinanceCalculator.byCategory(entries(cur, loans), Kind.DESPESA, c);

        List<MonthBarDto> bars = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            MonthTotals t = FinanceCalculator.totals(entries(months.getOrDefault(ym, List.of()), loans), c);
            bars.add(new MonthBarDto(ym.toString(), monthTag(ym), t.receitas(), t.despesas(), t.investido()));
        }

        LocalDate today = calendar.today();
        List<UpcomingDto> upcoming = upcoming(userId, today);
        List<String> tips = insights.write(new InsightWriter.Context(userId, m, profile.objectives(), current, curByCat,
                FinanceCalculator.byCategory(entries(prev, loans), Kind.DESPESA, c), cats, upcoming, cur));

        // Vazia enquanto não houver lançamento até hoje (previstos futuros, como o salário recorrente, não contam).
        boolean empty = !transactions.hasAnyUntil(userId, today);
        return new DashboardDto(m.toString(), empty, TotalsDto.of(current), TotalsDto.of(previous),
                slices(curByCat, current.despesas(), cats), bars, upcoming, tips);
    }

    /** Despesas previstas dos próximos 15 dias (contas e parcelas). */
    private List<UpcomingDto> upcoming(Long userId, LocalDate today) {
        return transactionService.range(userId, today, today.plusDays(UPCOMING_DAYS)).stream()
                .filter(t -> t.kind == Kind.DESPESA && t.status == Status.PREVISTO)
                .sorted(Comparator.comparing((Transaction t) -> t.date).thenComparing(t -> t.id))
                .limit(UPCOMING_LIMIT)
                .map(t -> new UpcomingDto(t.id, t.date, t.description, t.categoryId, t.amountCents,
                        t.installmentNumber, t.installmentTotal))
                .toList();
    }

    private static List<SliceDto> slices(Map<Long, Long> byCat, long total, Map<Long, Category> cats) {
        List<SliceDto> out = new ArrayList<>();
        for (Slice s : FinanceCalculator.topWithOthers(byCat, TOP_CATEGORIES)) {
            int bps = total == 0 ? 0 : (int) Math.round(s.amountCents() * 10_000.0 / total);
            if (s.isOthers()) {
                out.add(new SliceDto(null, "Outros", "ellipsis", "chart-other", s.amountCents(), bps));
            } else if (s.categoryId() == FinanceCalculator.CONTRIBUTION_KEY) {
                out.add(new SliceDto(s.categoryId(), "Contribuição", "hand-heart", "chart-other", s.amountCents(), bps));
            } else {
                Category cat = cats.get(s.categoryId());
                out.add(cat == null
                        ? new SliceDto(s.categoryId(), "Sem categoria", "tag", "chart-other", s.amountCents(), bps)
                        : new SliceDto(s.categoryId(), cat.name, cat.icon, cat.color, s.amountCents(), bps));
            }
        }
        return out;
    }
}
