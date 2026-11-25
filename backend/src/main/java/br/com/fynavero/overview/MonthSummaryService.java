package br.com.fynavero.overview;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.ledger.FinanceCalculator;
import br.com.fynavero.ledger.FinanceCalculator.Contribution;
import br.com.fynavero.ledger.FinanceCalculator.MonthTotals;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.ledger.FinanceCalculator.entries;

/** Resumo de um mês fechado (usado no e-mail do dia 1): quanto entrou, saiu, foi investido e sobrou. */
@Service
public class MonthSummaryService {
    private final TransactionService transactions;
    private final CategoryService categories;
    private final AccountService accounts;

    public MonthSummaryService(TransactionService transactions, CategoryService categories, AccountService accounts) {
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
    }

    /** topCategory é nulo quando não houve despesa no mês. */
    public record MonthSummary(YearMonth month, long receitas, long despesas, long investido, long saldo,
                               String topCategory, long topCategoryCents, int count, MonthTotals previous) {}

    @Transactional
    public MonthSummary summary(Long userId, YearMonth m) {
        Map<Long, Category> cats = categories.map(userId);
        var loans = CategoryService.loanIds(cats.values());
        Contribution c = accounts.contributionOf(userId);
        List<Transaction> cur = transactions.month(userId, m);
        List<Transaction> prev = transactions.month(userId, m.minusMonths(1));
        MonthTotals t = FinanceCalculator.totals(entries(cur, loans), c);

        String topName = null;
        long topCents = 0;
        for (var e : FinanceCalculator.byCategory(entries(cur, loans), Kind.DESPESA, c).entrySet()) {
            if (e.getValue() <= topCents) continue;
            topCents = e.getValue();
            Category cat = cats.get(e.getKey());
            topName = e.getKey() == FinanceCalculator.CONTRIBUTION_KEY ? "Contribuição"
                    : cat != null ? cat.name : "Sem categoria";
        }
        return new MonthSummary(m, t.receitas(), t.despesas(), t.investido(), t.saldo(), topName, topCents, cur.size(),
                FinanceCalculator.totals(entries(prev, loans), c));
    }
}
