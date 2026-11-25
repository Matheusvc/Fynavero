package br.com.fynavero.overview;

import br.com.fynavero.ledger.FinanceCalculator.Entry;
import br.com.fynavero.ledger.Transaction;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Converte lançamentos na entrada do {@link br.com.fynavero.ledger.FinanceCalculator} e agrupa por mês. */
public final class LedgerFigures {
    private LedgerFigures() {}

    public static List<Entry> entries(Collection<Transaction> list, Set<Long> loanCategories) {
        return list.stream()
                .map(t -> new Entry(t.kind, t.amountCents, t.categoryId, loanCategories.contains(t.categoryId)))
                .toList();
    }

    public static Map<YearMonth, List<Transaction>> byMonth(Collection<Transaction> list) {
        return list.stream().collect(Collectors.groupingBy(t -> YearMonth.from(t.date)));
    }
}
