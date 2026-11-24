package br.com.fynavero.planning;

import br.com.fynavero.account.AccountService;
import br.com.fynavero.calc.FinanceCalculator;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.domain.Kind;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.planning.PlanningDtos.BudgetDto;
import br.com.fynavero.planning.PlanningDtos.BudgetLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.overview.LedgerFigures.entries;

/**
 * Limite de gasto do mês por categoria de despesa (a "meta" da categoria). Conta tudo o que está lançado
 * no mês, inclusive contas previstas, para o aviso chegar antes do dinheiro acabar.
 */
@Service
public class BudgetService {
    /** 80% do limite, em pontos-base. */
    public static final int ALERT_BPS = 8_000;
    public static final int FULL_BPS = 10_000;

    private final TransactionService transactions;
    private final CategoryService categories;
    private final AccountService accounts;

    public BudgetService(TransactionService transactions, CategoryService categories, AccountService accounts) {
        this.transactions = transactions;
        this.categories = categories;
        this.accounts = accounts;
    }

    @Transactional
    public List<BudgetDto> status(Long userId, YearMonth m) {
        List<Category> limited = categories.list(userId).stream()
                .filter(c -> c.kind == Kind.DESPESA && c.goalCents != null && c.goalCents > 0)
                .toList();
        if (limited.isEmpty()) return List.of();
        Map<Long, Category> cats = categories.map(userId);
        Map<Long, Long> spent = FinanceCalculator.byCategory(
                entries(transactions.month(userId, m), CategoryService.loanIds(cats.values())),
                Kind.DESPESA, accounts.contributionOf(userId));
        return limited.stream()
                .map(c -> {
                    long s = spent.getOrDefault(c.id, 0L);
                    int bps = (int) Math.min(99_999, s * 10_000 / c.goalCents);
                    return new BudgetDto(c.id, c.name, c.icon, c.color, c.goalCents, s, bps, level(bps));
                })
                .sorted(Comparator.comparingInt(BudgetDto::pctBps).reversed())
                .toList();
    }

    public static BudgetLevel level(int bps) {
        if (bps >= FULL_BPS) return BudgetLevel.ESTOURADO;
        if (bps >= ALERT_BPS) return BudgetLevel.ALERTA;
        return BudgetLevel.OK;
    }
}
