package br.com.fynavero.overview;

import br.com.fynavero.ledger.FinanceCalculator.MonthTotals;
import br.com.fynavero.category.Category;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.account.Profile.Objective;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.Transaction.TransactionRepository;
import br.com.fynavero.overview.OverviewDtos.UpcomingDto;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.shared.Formats.brl;

/** Até 3 dicas do Início, em linguagem simples, personalizadas pelos objetivos escolhidos. */
@Component
class InsightWriter {
    private static final int MAX_INSIGHTS = 3;
    /** Aumento por categoria que vira dica: pelo menos R$ 50 e 20% acima do mês anterior. */
    private static final long MIN_INCREASE_CENTS = 5_000;
    private static final int MIN_INCREASE_PCT = 20;

    private final TransactionRepository transactions;

    InsightWriter(TransactionRepository transactions) {
        this.transactions = transactions;
    }

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
