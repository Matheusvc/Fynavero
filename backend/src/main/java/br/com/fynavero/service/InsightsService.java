package br.com.fynavero.service;

import br.com.fynavero.api.Dto.*;
import br.com.fynavero.calc.FinanceCalculator;
import br.com.fynavero.calc.FinanceCalculator.Contribution;
import br.com.fynavero.calc.FinanceCalculator.Entry;
import br.com.fynavero.calc.FinanceCalculator.MonthTotals;
import br.com.fynavero.calc.FinanceCalculator.Slice;
import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Enums.Objective;
import br.com.fynavero.domain.Enums.Status;
import br.com.fynavero.domain.Repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static br.com.fynavero.support.Support.*;

/** Telas de leitura: Início, Parcelas e Dívidas, Investimentos e Relatórios. */
@Service
public class InsightsService {
    private final LedgerService ledger;
    private final CategoryService categories;
    private final ProfileRepo profiles;
    private final TransactionRepo txs;
    private final InstallmentPlanRepo plans;
    private final RecurrenceRepo recs;

    public InsightsService(LedgerService ledger, CategoryService categories, ProfileRepo profiles,
                           TransactionRepo txs, InstallmentPlanRepo plans, RecurrenceRepo recs) {
        this.ledger = ledger;
        this.categories = categories;
        this.profiles = profiles;
        this.txs = txs;
        this.plans = plans;
        this.recs = recs;
    }

    public Contribution contribution(Long userId) {
        Profile p = profiles.findById(userId).orElseThrow(() -> notFound());
        return new Contribution(p.contributionEnabled, p.contributionBps, p.contributionCategoryId);
    }

    static List<Entry> entries(Collection<Transaction> list, Set<Long> loanCategories) {
        return list.stream().map(t -> new Entry(t.kind, t.amountCents, t.categoryId, loanCategories.contains(t.categoryId))).toList();
    }

    static Set<Long> loanIds(Collection<Category> cats) {
        return cats.stream().filter(c -> c.loan).map(c -> c.id).collect(Collectors.toSet());
    }

    static TotalsDto dto(MonthTotals t) {
        return new TotalsDto(t.receitas(), t.emprestimos(), t.despesas(), t.despesasLancadas(), t.contribuicao(), t.sobra(),
                t.investido(), t.saldo());
    }

    static Map<YearMonth, List<Transaction>> byMonth(Collection<Transaction> list) {
        return list.stream().collect(Collectors.groupingBy(t -> YearMonth.from(t.date)));
    }

    static String label(YearMonth m) {
        return monthShort(m) + "/" + String.valueOf(m.getYear()).substring(2);
    }

    // ------------------------------------------------------------------ Início

    @Transactional
    public DashboardDto dashboard(Long userId, YearMonth m) {
        Contribution c = contribution(userId);
        Map<Long, Category> cats = categories.map(userId);
        Set<Long> loans = loanIds(cats.values());
        Map<YearMonth, List<Transaction>> months = byMonth(ledger.range(userId, m.minusMonths(5).atDay(1), m.atEndOfMonth()));

        List<Transaction> cur = months.getOrDefault(m, List.of());
        List<Transaction> prev = months.getOrDefault(m.minusMonths(1), List.of());
        MonthTotals current = FinanceCalculator.totals(entries(cur, loans), c);
        MonthTotals previous = FinanceCalculator.totals(entries(prev, loans), c);

        Map<Long, Long> curByCat = FinanceCalculator.byCategory(entries(cur, loans), Kind.DESPESA, c);
        List<SliceDto> breakdown = slices(curByCat, current.despesas(), cats);

        List<MonthBarDto> bars = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            MonthTotals t = FinanceCalculator.totals(entries(months.getOrDefault(ym, List.of()), loans), c);
            bars.add(new MonthBarDto(ym.toString(), label(ym), t.receitas(), t.despesas(), t.investido()));
        }

        LocalDate today = ledger.today();
        List<UpcomingDto> upcoming = ledger.range(userId, today, today.plusDays(15)).stream()
                .filter(t -> t.kind == Kind.DESPESA && t.status == Status.PREVISTO)
                .sorted(Comparator.comparing((Transaction t) -> t.date).thenComparing(t -> t.id))
                .limit(8)
                .map(t -> new UpcomingDto(t.id, t.date, t.description, t.categoryId, t.amountCents,
                        t.installmentNumber, t.installmentTotal))
                .toList();

        List<Objective> objectives = profiles.findById(userId).map(Profile::objectives).orElse(List.of());
        List<String> insights = insights(objectives, current, curByCat,
                FinanceCalculator.byCategory(entries(prev, loans), Kind.DESPESA, c), cats, upcoming, cur, userId, m);

        // Vazia enquanto não houver nenhum lançamento até hoje (previstos futuros, como o salário recorrente, não contam).
        return new DashboardDto(m.toString(), !txs.hasAnyUntil(userId, today), dto(current), dto(previous), breakdown, bars,
                upcoming, insights);
    }

    private List<SliceDto> slices(Map<Long, Long> byCat, long total, Map<Long, Category> cats) {
        List<SliceDto> out = new ArrayList<>();
        for (Slice s : FinanceCalculator.topWithOthers(byCat, 6)) {
            int bps = total == 0 ? 0 : (int) Math.round(s.amountCents() * 10_000.0 / total);
            if (s.isOthers()) {
                out.add(new SliceDto(null, "Outros", "ellipsis", "chart-other", s.amountCents(), bps));
            } else if (s.categoryId() == FinanceCalculator.CONTRIBUTION_KEY) {
                out.add(new SliceDto(s.categoryId(), "Contribuição", "hand-heart", "chart-other", s.amountCents(), bps));
            } else {
                Category cat = cats.get(s.categoryId());
                out.add(new SliceDto(s.categoryId(), cat == null ? "Sem categoria" : cat.name,
                        cat == null ? "tag" : cat.icon, cat == null ? "chart-other" : cat.color, s.amountCents(), bps));
            }
        }
        return out;
    }

    /** No máximo 3 frases, em linguagem humana, personalizadas pelos objetivos escolhidos. */
    private List<String> insights(List<Objective> objectives, MonthTotals t, Map<Long, Long> cur, Map<Long, Long> prev,
                                  Map<Long, Category> cats, List<UpcomingDto> upcoming, List<Transaction> monthTxs,
                                  Long userId, YearMonth m) {
        List<String> out = new ArrayList<>();
        if (t.receitas() == 0 && t.despesas() == 0 && t.investido() == 0) {
            out.add("Comece lançando sua renda e seus primeiros gastos deste mês. Leva poucos segundos.");
            return out;
        }
        if (t.saldo() < 0) {
            out.add("Atenção: o saldo para passar o mês está negativo em " + brl(-t.saldo())
                    + ". Vale rever os gastos que ainda dá para adiar.");
        }
        if (t.emprestimos() > 0) {
            out.add("Entraram " + brl(t.emprestimos()) + " de empréstimo neste mês. As parcelas do pagamento ficam em Parcelas e Dívidas.");
        }

        Long worstCat = null;
        long worstDiff = 0;
        for (var e : cur.entrySet()) {
            long before = prev.getOrDefault(e.getKey(), 0L);
            long diff = e.getValue() - before;
            if (before > 0 && diff > 5_000 && diff * 100 / before >= 20 && diff > worstDiff) {
                worstDiff = diff;
                worstCat = e.getKey();
            }
        }
        if (worstCat != null && cats.containsKey(worstCat)) {
            out.add("Você gastou " + brl(worstDiff) + " a mais com " + cats.get(worstCat).name
                    + " do que no mês passado.");
        }

        if (!upcoming.isEmpty()) {
            long sum = upcoming.stream().mapToLong(UpcomingDto::amountCents).sum();
            out.add(upcoming.size() == 1
                    ? "Você tem 1 conta para pagar nos próximos 15 dias, de " + brl(sum) + "."
                    : "Você tem " + upcoming.size() + " contas para pagar nos próximos 15 dias, somando " + brl(sum) + ".");
        }

        for (Objective objective : objectives) {
            switch (objective) {
                case INVESTIR -> out.add(t.investido() == 0
                        ? "Seu objetivo é começar a investir. Que tal separar um valor fixo assim que a renda cair?"
                        : "Você já investiu " + brl(t.investido()) + " neste mês. Continue assim!");
                case DIVIDAS -> {
                    long parcels = monthTxs.stream().filter(x -> x.installmentPlanId != null).mapToLong(x -> x.amountCents).sum();
                    if (parcels > 0) out.add("Suas parcelas somam " + brl(parcels) + " neste mês. Cada uma paga é um passo para sair das dívidas.");
                }
                case OBJETIVO -> categories.list(userId).stream()
                        .filter(cat -> cat.kind == Kind.INVESTIMENTO && cat.goalCents != null && cat.goalCents > 0)
                        .findFirst()
                        .ifPresent(cat -> {
                            long acc = txs.findKindUpTo(userId, Kind.INVESTIMENTO, m.atEndOfMonth()).stream()
                                    .filter(x -> cat.id.equals(x.categoryId)).mapToLong(x -> x.amountCents).sum();
                            long pct = Math.min(100, acc * 100 / cat.goalCents);
                            out.add("Você já juntou " + pct + "% da meta \"" + cat.name + "\".");
                        });
                case ORGANIZAR -> out.add("Você registrou " + monthTxs.size()
                        + (monthTxs.size() == 1 ? " lançamento" : " lançamentos") + " neste mês. Registrar tudo é o que dá clareza.");
            }
        }
        if (t.saldo() > 0 && out.size() < 3) {
            out.add("Sobram " + brl(t.saldo()) + " para passar o mês depois dos investimentos.");
        }
        return out.stream().limit(3).toList();
    }

    // ------------------------------------------------------------------ Parcelas e Dívidas

    @Transactional
    public InstallmentsDto installments(Long userId, YearMonth m) {
        ledger.ensureGenerated(userId, m.plusMonths(11));
        LocalDate today = ledger.today();
        Map<Long, List<Transaction>> byPlan = txs.findAllInstallments(userId).stream()
                .collect(Collectors.groupingBy(t -> t.installmentPlanId));

        List<PlanDto> out = new ArrayList<>();
        for (InstallmentPlan p : plans.findByUserIdOrderByFirstDateDesc(userId)) {
            List<Transaction> parcels = byPlan.get(p.id);
            if (parcels == null || parcels.isEmpty()) continue;
            parcels.sort(Comparator.comparing(t -> t.installmentNumber));
            int paid = (int) parcels.stream().filter(t -> !t.date.isAfter(today)).count();
            int current = (int) parcels.stream().filter(t -> !YearMonth.from(t.date).isAfter(m)).count();
            long remaining = parcels.stream().filter(t -> t.date.isAfter(today)).mapToLong(t -> t.amountCents).sum();
            Transaction last = parcels.getLast();
            out.add(new PlanDto(p.id, p.description, p.categoryId, parcels.getFirst().amountCents,
                    parcels.stream().mapToLong(t -> t.amountCents).sum(), p.installmentCount, paid, current, remaining,
                    monthName(YearMonth.from(last.date)) + " " + last.date.getYear(), p.firstDate, remaining == 0));
        }
        out.sort(Comparator.comparing(PlanDto::finished).thenComparing(PlanDto::firstDate, Comparator.reverseOrder()));

        Set<Long> fixedRecs = recs.findByUserIdOrderByDayOfMonth(userId).stream()
                .filter(r -> r.kind == Kind.DESPESA).map(r -> r.id).collect(Collectors.toSet());
        Map<YearMonth, List<Transaction>> months = byMonth(txs.findRange(userId, m.atDay(1), m.plusMonths(11).atEndOfMonth()));
        List<CommitmentDto> timeline = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth ym = m.plusMonths(i);
            List<Transaction> list = months.getOrDefault(ym, List.of());
            long inst = list.stream().filter(t -> t.installmentPlanId != null).mapToLong(t -> t.amountCents).sum();
            long fixed = list.stream().filter(t -> t.recurrenceId != null && fixedRecs.contains(t.recurrenceId))
                    .mapToLong(t -> t.amountCents).sum();
            timeline.add(new CommitmentDto(ym.toString(), label(ym), inst, fixed));
        }
        return new InstallmentsDto(out, timeline, timeline.getFirst().installments());
    }

    // ------------------------------------------------------------------ Investimentos

    @Transactional
    public InvestmentsDto investments(Long userId, YearMonth m) {
        ledger.ensureGenerated(userId, m);
        List<Transaction> upTo = txs.findKindUpTo(userId, Kind.INVESTIMENTO, m.atEndOfMonth());
        Map<Long, Long> acc = new HashMap<>();
        Map<Long, Long> month = new HashMap<>();
        Map<YearMonth, Long> hist = new HashMap<>();
        for (Transaction t : upTo) {
            acc.merge(t.categoryId, t.amountCents, Math::addExact);
            YearMonth ym = YearMonth.from(t.date);
            if (ym.equals(m)) month.merge(t.categoryId, t.amountCents, Math::addExact);
            hist.merge(ym, t.amountCents, Math::addExact);
        }
        List<InvestmentCategoryDto> cats = new ArrayList<>();
        for (Category c : categories.list(userId)) {
            if (c.kind != Kind.INVESTIMENTO) continue;
            long a = acc.getOrDefault(c.id, 0L);
            if (c.hidden && a == 0) continue;
            cats.add(new InvestmentCategoryDto(c.id, c.name, c.icon, c.color, month.getOrDefault(c.id, 0L), a, c.goalCents));
        }
        List<MonthBarDto> history = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            history.add(new MonthBarDto(ym.toString(), label(ym), 0, 0, hist.getOrDefault(ym, 0L)));
        }
        long monthTotal = month.values().stream().mapToLong(Long::longValue).sum();
        long accTotal = acc.values().stream().mapToLong(Long::longValue).sum();
        return new InvestmentsDto(m.toString(), monthTotal, accTotal, cats, history);
    }

    // ------------------------------------------------------------------ Relatórios

    @Transactional
    public AnnualReport annual(Long userId, int year) {
        if (year < 2000 || year > 2100) throw badRequest("Ano inválido.");
        Contribution c = contribution(userId);
        List<Category> catList = categories.list(userId);
        Set<Long> loans = loanIds(catList);
        Map<YearMonth, List<Transaction>> months = byMonth(ledger.range(userId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));

        List<String> labels = new ArrayList<>();
        @SuppressWarnings("unchecked") Map<Long, Long>[] inc = new Map[12], exp = new Map[12], inv = new Map[12];
        MonthTotals[] totals = new MonthTotals[12];
        for (int i = 0; i < 12; i++) {
            YearMonth ym = YearMonth.of(year, i + 1);
            labels.add(monthShort(ym));
            List<Entry> e = entries(months.getOrDefault(ym, List.of()), loans);
            inc[i] = FinanceCalculator.byCategory(e, Kind.RECEITA, c);
            exp[i] = FinanceCalculator.byCategory(e, Kind.DESPESA, c);
            inv[i] = FinanceCalculator.byCategory(e, Kind.INVESTIMENTO, c);
            totals[i] = FinanceCalculator.totals(e, c);
        }

        List<ReportRow> receitas = rows(catList.stream().filter(x -> !x.loan).toList(), Kind.RECEITA, null, inc);
        List<ReportRow> emprestimos = rows(catList.stream().filter(x -> x.loan).toList(), Kind.RECEITA, null, inc);
        List<ReportGroup> groups = new ArrayList<>();
        List<String> groupOrder = new ArrayList<>(CategoryService.EXPENSE_GROUPS);
        catList.stream().filter(x -> x.kind == Kind.DESPESA && !groupOrder.contains(x.group)).forEach(x -> groupOrder.add(x.group));
        for (String g : groupOrder) {
            List<ReportRow> rows = new ArrayList<>(rows(catList, Kind.DESPESA, g, exp));
            if (g.equals("Contribuições")) {
                long[] contrib = new long[12];
                for (int i = 0; i < 12; i++) contrib[i] = exp[i].getOrDefault(FinanceCalculator.CONTRIBUTION_KEY, 0L);
                if (Arrays.stream(contrib).anyMatch(v -> v != 0)) rows.add(row(null, "Contribuição", contrib));
            }
            if (rows.isEmpty()) continue;
            long[] sub = new long[12];
            for (ReportRow r : rows) for (int i = 0; i < 12; i++) sub[i] += r.months()[i];
            groups.add(new ReportGroup(g, rows, row(null, "Subtotal " + g, sub)));
        }
        List<ReportRow> investimentos = rows(catList, Kind.INVESTIMENTO, null, inv);

        long[] desp = new long[12], invest = new long[12], sobra = new long[12], saldo = new long[12];
        for (int i = 0; i < 12; i++) {
            desp[i] = totals[i].despesas();
            invest[i] = totals[i].investido();
            sobra[i] = totals[i].sobra();
            saldo[i] = totals[i].saldo();
        }
        List<ReportRow> resultado = List.of(
                row(null, "Sobra do mês", sobra),
                row(null, "Investido", invest),
                row(null, "Saldo para passar o mês", saldo));
        return new AnnualReport(year, labels, receitas, emprestimos, groups, row(null, "Total de despesas", desp),
                investimentos, row(null, "Total investido", invest), resultado);
    }

    private static List<ReportRow> rows(List<Category> cats, Kind kind, String group, Map<Long, Long>[] perMonth) {
        List<ReportRow> out = new ArrayList<>();
        for (Category cat : cats) {
            if (cat.kind != kind || (group != null && !group.equals(cat.group))) continue;
            long[] v = new long[12];
            for (int i = 0; i < 12; i++) v[i] = perMonth[i].getOrDefault(cat.id, 0L);
            if (Arrays.stream(v).allMatch(x -> x == 0)) continue;
            out.add(row(cat.id, cat.name, v));
        }
        return out;
    }

    private static ReportRow row(Long id, String label, long[] v) {
        long total = 0;
        for (long x : v) total = Math.addExact(total, x);
        return new ReportRow(id, label, v, total, FinanceCalculator.averageOfNonZero(v));
    }

    /** Mês atual × mês anterior × média dos 12 meses anteriores (só meses com valor). */
    @Transactional
    public CompareReport compare(Long userId, YearMonth m) {
        Contribution c = contribution(userId);
        Map<Long, Category> cats = categories.map(userId);
        Set<Long> loans = loanIds(cats.values());
        Map<YearMonth, List<Transaction>> months = byMonth(ledger.range(userId, m.minusMonths(12).atDay(1), m.atEndOfMonth()));

        MonthTotals[] t = new MonthTotals[13];
        List<Map<Long, Long>> exp = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            List<Entry> e = entries(months.getOrDefault(m.minusMonths(12 - i), List.of()), loans);
            t[i] = FinanceCalculator.totals(e, c);
            exp.add(FinanceCalculator.byCategory(e, Kind.DESPESA, c));
        }
        record Metric(String label, java.util.function.ToLongFunction<MonthTotals> f) {}
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
            long[] hist = new long[12];
            for (int i = 0; i < 12; i++) hist[i] = mt.f().applyAsLong(t[i]);
            totals.add(new CompareRow(null, mt.label(), mt.f().applyAsLong(t[12]), hist[11],
                    FinanceCalculator.averageOfNonZero(hist)));
        }
        Set<Long> keys = new LinkedHashSet<>();
        exp.forEach(x -> keys.addAll(x.keySet()));
        List<CompareRow> rows = new ArrayList<>();
        for (Long k : keys) {
            long[] hist = new long[12];
            for (int i = 0; i < 12; i++) hist[i] = exp.get(i).getOrDefault(k, 0L);
            long current = exp.get(12).getOrDefault(k, 0L);
            String name = k == FinanceCalculator.CONTRIBUTION_KEY ? "Contribuição"
                    : cats.containsKey(k) ? cats.get(k).name : "Sem categoria";
            rows.add(new CompareRow(k, name, current, hist[11], FinanceCalculator.averageOfNonZero(hist)));
        }
        rows.sort(Comparator.comparingLong(CompareRow::current).reversed().thenComparing(CompareRow::label));
        return new CompareReport(m.toString(), m.minusMonths(1).toString(), totals, rows);
    }
}
