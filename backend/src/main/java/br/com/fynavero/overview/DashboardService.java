package br.com.fynavero.overview;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.account.Profile;
import br.com.fynavero.account.Profile.Objective;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.FinanceCalculator;
import br.com.fynavero.ledger.FinanceCalculator.Contribution;
import br.com.fynavero.ledger.FinanceCalculator.MonthTotals;
import br.com.fynavero.ledger.FinanceCalculator.Slice;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.Status;
import br.com.fynavero.ledger.Transaction.TransactionRepository;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.overview.OverviewDtos.DashboardDto;
import br.com.fynavero.overview.OverviewDtos.MonthBarDto;
import br.com.fynavero.overview.OverviewDtos.SliceDto;
import br.com.fynavero.overview.OverviewDtos.TotalsDto;
import br.com.fynavero.overview.OverviewDtos.UpcomingDto;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static br.com.fynavero.ledger.FinanceCalculator.byMonth;
import static br.com.fynavero.ledger.FinanceCalculator.entries;
import static br.com.fynavero.shared.Formats.brl;
import static br.com.fynavero.shared.Formats.monthTag;

/** Tela Início: saldo para passar o mês, totais, "Para onde foi seu dinheiro", 6 meses, vencimentos e dicas. */
@Service
public class DashboardService {
    private final TransactionService transactionService;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final AccountService accounts;
    private final LedgerCalendar calendar;

    public DashboardService(TransactionService transactionService, TransactionRepository transactions, CategoryService categories, AccountService accounts, LedgerCalendar calendar) {
        this.transactionService = transactionService;
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
        this.calendar = calendar;
    }

    private static final int TOP_CATEGORIES = 6;
    private static final int UPCOMING_DAYS = 15;
    private static final int UPCOMING_LIMIT = 8;

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
        List<String> tips = write(new DashboardService.Context(userId, m, profile.objectives(), current, curByCat,
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

    // ================================================================ Dicas do "Para você" (no máximo 3)
    private static final int MAX_INSIGHTS = 3;
    /** Aumento por categoria que vira dica: pelo menos R$ 50 e 20% acima do mês anterior. */
    private static final long MIN_INCREASE_CENTS = 5_000;
    private static final int MIN_INCREASE_PCT = 20;

    record Context(Long userId, YearMonth month, List<Objective> objectives, MonthTotals totals,
                   Map<Long, Long> expensesByCategory, Map<Long, Long> previousByCategory,
                   Map<Long, Category> categories, List<UpcomingDto> upcoming, List<Transaction> monthTransactions) {}

    List<String> write(Context c) {
        MonthTotals t = c.totals();
        List<String> out = new ArrayList<>();
        if (t.receitas() == 0 && t.despesas() == 0 && t.investido() == 0) {
            return List.of("Comece lançando sua renda e seus primeiros gastos deste mês. Leva poucos segundos.");
        }
        if (t.saldo() < 0) {
            out.add("Atenção: o saldo para passar o mês está negativo em " + brl(-t.saldo())
                    + ". Vale rever os gastos que ainda dá para adiar.");
        }
        if (t.emprestimos() > 0) {
            out.add("Entraram " + brl(t.emprestimos()) + " de empréstimo neste mês. As parcelas do pagamento ficam em Parcelas e Dívidas.");
        }
        String increase = biggestIncrease(c);
        if (increase != null) out.add(increase);
        if (!c.upcoming().isEmpty()) out.add(upcomingBills(c.upcoming()));
        for (Objective objective : c.objectives()) {
            String s = forObjective(objective, c);
            if (s != null) out.add(s);
        }
        if (t.saldo() > 0 && out.size() < MAX_INSIGHTS) {
            out.add("Sobram " + brl(t.saldo()) + " para passar o mês depois dos investimentos.");
        }
        return out.stream().limit(MAX_INSIGHTS).toList();
    }

    private static String biggestIncrease(Context c) {
        Long worstCat = null;
        long worstDiff = 0;
        for (var e : c.expensesByCategory().entrySet()) {
            long before = c.previousByCategory().getOrDefault(e.getKey(), 0L);
            long diff = e.getValue() - before;
            if (before > 0 && diff > MIN_INCREASE_CENTS && diff * 100 / before >= MIN_INCREASE_PCT && diff > worstDiff) {
                worstDiff = diff;
                worstCat = e.getKey();
            }
        }
        if (worstCat == null || !c.categories().containsKey(worstCat)) return null;
        return "Você gastou " + brl(worstDiff) + " a mais com " + c.categories().get(worstCat).name + " do que no mês passado.";
    }

    private static String upcomingBills(List<UpcomingDto> upcoming) {
        long sum = upcoming.stream().mapToLong(UpcomingDto::amountCents).sum();
        return upcoming.size() == 1
                ? "Você tem 1 conta para pagar nos próximos 15 dias, de " + brl(sum) + "."
                : "Você tem " + upcoming.size() + " contas para pagar nos próximos 15 dias, somando " + brl(sum) + ".";
    }

    private String forObjective(Objective objective, Context c) {
        MonthTotals t = c.totals();
        List<Transaction> monthTxs = c.monthTransactions();
        return switch (objective) {
            case INVESTIR -> t.investido() == 0
                    ? "Seu objetivo é começar a investir. Que tal separar um valor fixo assim que a renda cair?"
                    : "Você já investiu " + brl(t.investido()) + " neste mês. Continue assim!";
            case DIVIDAS -> {
                long parcels = monthTxs.stream().filter(x -> x.installmentPlanId != null).mapToLong(x -> x.amountCents).sum();
                yield parcels > 0
                        ? "Suas parcelas somam " + brl(parcels) + " neste mês. Cada uma paga é um passo para sair das dívidas."
                        : null;
            }
            case OBJETIVO -> goalProgress(c);
            case ORGANIZAR -> "Você registrou " + monthTxs.size() + (monthTxs.size() == 1 ? " lançamento" : " lançamentos")
                    + " neste mês. Registrar tudo é o que dá clareza.";
        };
    }

    private String goalProgress(Context c) {
        return c.categories().values().stream()
                .filter(cat -> cat.kind == Kind.INVESTIMENTO && cat.goalCents != null && cat.goalCents > 0)
                .min((a, b) -> Integer.compare(a.sortOrder, b.sortOrder))
                .map(cat -> {
                    long saved = transactions.findKindUpTo(c.userId(), Kind.INVESTIMENTO, c.month().atEndOfMonth()).stream()
                            .filter(x -> cat.id.equals(x.categoryId)).mapToLong(x -> x.amountCents).sum();
                    return "Você já juntou " + Math.min(100, saved * 100 / cat.goalCents) + "% da meta \"" + cat.name + "\".";
                })
                .orElse(null);
    }
}
