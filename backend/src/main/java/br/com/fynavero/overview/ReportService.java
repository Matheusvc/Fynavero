package br.com.fynavero.overview;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.ledger.FinanceCalculator;
import br.com.fynavero.ledger.FinanceCalculator.Contribution;
import br.com.fynavero.ledger.FinanceCalculator.Entry;
import br.com.fynavero.ledger.FinanceCalculator.MonthTotals;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.overview.OverviewDtos.AnnualReport;
import br.com.fynavero.overview.OverviewDtos.CompareReport;
import br.com.fynavero.overview.OverviewDtos.CompareRow;
import br.com.fynavero.overview.OverviewDtos.ReportGroup;
import br.com.fynavero.overview.OverviewDtos.ReportRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Formats.monthShort;
import static br.com.fynavero.overview.LedgerFigures.byMonth;
import static br.com.fynavero.overview.LedgerFigures.entries;

/** Relatório anual (categorias × meses) e comparativo (mês atual × anterior × média). */
@Service
public class ReportService {
    private static final int MONTHS = 12;

    private final TransactionService transactions;
    private final CategoryService categories;
    private final AccountService accounts;

    public ReportService(TransactionService transactions, CategoryService categories, AccountService accounts) {
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
    }

    @Transactional
    public AnnualReport annual(Long userId, int year) {
        if (year < 2000 || year > 2100) throw badRequest("Ano inválido.");
        Contribution c = accounts.contributionOf(userId);
        List<Category> catList = categories.list(userId);
        Set<Long> loans = CategoryService.loanIds(catList);
        Map<YearMonth, List<Transaction>> months = byMonth(transactions.range(userId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));

        List<String> labels = new ArrayList<>();
        List<Map<Long, Long>> income = new ArrayList<>(), expense = new ArrayList<>(), invest = new ArrayList<>();
        MonthTotals[] totals = new MonthTotals[MONTHS];
        for (int i = 0; i < MONTHS; i++) {
            YearMonth ym = YearMonth.of(year, i + 1);
            labels.add(monthShort(ym));
            List<Entry> e = entries(months.getOrDefault(ym, List.of()), loans);
            income.add(FinanceCalculator.byCategory(e, Kind.RECEITA, c));
            expense.add(FinanceCalculator.byCategory(e, Kind.DESPESA, c));
            invest.add(FinanceCalculator.byCategory(e, Kind.INVESTIMENTO, c));
            totals[i] = FinanceCalculator.totals(e, c);
        }

        List<ReportRow> receitas = rows(catList.stream().filter(x -> !x.loan).toList(), Kind.RECEITA, null, income);
        List<ReportRow> emprestimos = rows(catList.stream().filter(x -> x.loan).toList(), Kind.RECEITA, null, income);
        List<ReportGroup> groups = expenseGroups(catList, expense);
        List<ReportRow> investimentos = rows(catList, Kind.INVESTIMENTO, null, invest);

        List<ReportRow> resultado = List.of(
                metric("Sobra do mês", totals, MonthTotals::sobra),
                metric("Investido", totals, MonthTotals::investido),
                metric("Saldo para passar o mês", totals, MonthTotals::saldo));
        return new AnnualReport(year, labels, receitas, emprestimos, groups,
                metric("Total de despesas", totals, MonthTotals::despesas), investimentos,
                metric("Total investido", totals, MonthTotals::investido), resultado);
    }

    /** Despesas agrupadas na ordem dos grupos, com subtotal; a contribuição sem categoria entra em "Contribuições". */
    private static List<ReportGroup> expenseGroups(List<Category> catList, List<Map<Long, Long>> expense) {
        List<String> order = new ArrayList<>(CategoryService.EXPENSE_GROUPS);
        catList.stream().filter(x -> x.kind == Kind.DESPESA && !order.contains(x.group)).forEach(x -> order.add(x.group));
        List<ReportGroup> groups = new ArrayList<>();
        for (String g : order) {
            List<ReportRow> rows = new ArrayList<>(rows(catList, Kind.DESPESA, g, expense));
            if (g.equals("Contribuições")) {
                long[] contrib = new long[MONTHS];
                for (int i = 0; i < MONTHS; i++) contrib[i] = expense.get(i).getOrDefault(FinanceCalculator.CONTRIBUTION_KEY, 0L);
                if (Arrays.stream(contrib).anyMatch(v -> v != 0)) rows.add(row(null, "Contribuição", contrib));
            }
            if (rows.isEmpty()) continue;
            long[] sub = new long[MONTHS];
            for (ReportRow r : rows) for (int i = 0; i < MONTHS; i++) sub[i] += r.months()[i];
            groups.add(new ReportGroup(g, rows, row(null, "Subtotal " + g, sub)));
        }
        return groups;
    }

    /** Uma linha por categoria do tipo (e grupo, se informado); categorias sem valor no ano ficam de fora. */
    private static List<ReportRow> rows(List<Category> cats, Kind kind, String group, List<Map<Long, Long>> perMonth) {
        List<ReportRow> out = new ArrayList<>();
        for (Category cat : cats) {
            if (cat.kind != kind || (group != null && !group.equals(cat.group))) continue;
            long[] v = new long[MONTHS];
            for (int i = 0; i < MONTHS; i++) v[i] = perMonth.get(i).getOrDefault(cat.id, 0L);
            if (Arrays.stream(v).anyMatch(x -> x != 0)) out.add(row(cat.id, cat.name, v));
        }
        return out;
    }

    private static ReportRow metric(String label, MonthTotals[] totals, ToLongFunction<MonthTotals> f) {
        return row(null, label, Arrays.stream(totals).mapToLong(f).toArray());
    }

    private static ReportRow row(Long id, String label, long[] v) {
        long total = 0;
        for (long x : v) total = Math.addExact(total, x);
        return new ReportRow(id, label, v, total, FinanceCalculator.averageOfNonZero(v));
    }

    /** Mês atual × mês anterior × média dos 12 meses anteriores (só meses com valor). */
    @Transactional
    public CompareReport compare(Long userId, YearMonth m) {
        Contribution c = accounts.contributionOf(userId);
        Map<Long, Category> cats = categories.map(userId);
        Set<Long> loans = CategoryService.loanIds(cats.values());
        Map<YearMonth, List<Transaction>> months = byMonth(transactions.range(userId, m.minusMonths(MONTHS).atDay(1), m.atEndOfMonth()));

        // posições 0..11 = os 12 meses anteriores; 12 = mês atual
        MonthTotals[] t = new MonthTotals[MONTHS + 1];
        List<Map<Long, Long>> expense = new ArrayList<>();
        for (int i = 0; i <= MONTHS; i++) {
            List<Entry> e = entries(months.getOrDefault(m.minusMonths(MONTHS - i), List.of()), loans);
            t[i] = FinanceCalculator.totals(e, c);
            expense.add(FinanceCalculator.byCategory(e, Kind.DESPESA, c));
        }

        record Metric(String label, ToLongFunction<MonthTotals> f) {}
        List<Metric> metrics = new ArrayList<>(List.of(
                new Metric("Receitas", MonthTotals::receitas),
                new Metric("Despesas", MonthTotals::despesas),
                new Metric("Investido", MonthTotals::investido),
                new Metric("Sobra do mês", MonthTotals::sobra),
                new Metric("Saldo para passar o mês", MonthTotals::saldo)));
        if (Arrays.stream(t).anyMatch(x -> x.emprestimos() > 0)) {
            metrics.add(1, new Metric("Empréstimos recebidos (incluídos nas receitas)", MonthTotals::emprestimos));
        }
        List<CompareRow> totals = new ArrayList<>();
        for (Metric mt : metrics) {
            long[] hist = new long[MONTHS];
            for (int i = 0; i < MONTHS; i++) hist[i] = mt.f().applyAsLong(t[i]);
            totals.add(new CompareRow(null, mt.label(), mt.f().applyAsLong(t[MONTHS]), hist[MONTHS - 1],
                    FinanceCalculator.averageOfNonZero(hist)));
        }

        Set<Long> keys = new LinkedHashSet<>();
        expense.forEach(x -> keys.addAll(x.keySet()));
        List<CompareRow> rows = new ArrayList<>();
        for (Long k : keys) {
            long[] hist = new long[MONTHS];
            for (int i = 0; i < MONTHS; i++) hist[i] = expense.get(i).getOrDefault(k, 0L);
            String name = k == FinanceCalculator.CONTRIBUTION_KEY ? "Contribuição"
                    : cats.containsKey(k) ? cats.get(k).name : "Sem categoria";
            rows.add(new CompareRow(k, name, expense.get(MONTHS).getOrDefault(k, 0L), hist[MONTHS - 1],
                    FinanceCalculator.averageOfNonZero(hist)));
        }
        rows.sort(Comparator.comparingLong(CompareRow::current).reversed().thenComparing(CompareRow::label));
        return new CompareReport(m.toString(), m.minusMonths(1).toString(), totals, rows);
    }
}
