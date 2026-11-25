package br.com.fynavero.overview;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.RecurrenceService;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionRepository;
import br.com.fynavero.overview.OverviewDtos.InvestmentCategoryDto;
import br.com.fynavero.overview.OverviewDtos.InvestmentsDto;
import br.com.fynavero.overview.OverviewDtos.MonthBarDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.shared.Formats.monthTag;

/** Tela Investimentos: total do mês, acumulado por categoria, progresso da meta e histórico de 12 meses. */
@Service
public class InvestmentOverviewService {
    private static final int HISTORY_MONTHS = 12;

    private final TransactionRepository transactions;
    private final RecurrenceService recurrences;
    private final CategoryService categories;

    public InvestmentOverviewService(TransactionRepository transactions, RecurrenceService recurrences,
                                     CategoryService categories) {
        this.transactions = transactions;
        this.recurrences = recurrences;
        this.categories = categories;
    }

    @Transactional
    public InvestmentsDto overview(Long userId, YearMonth m) {
        recurrences.ensureGenerated(userId, m);
        Map<Long, Long> accumulated = new HashMap<>();
        Map<Long, Long> inMonth = new HashMap<>();
        Map<YearMonth, Long> perMonth = new HashMap<>();
        for (Transaction t : transactions.findKindUpTo(userId, Kind.INVESTIMENTO, m.atEndOfMonth())) {
            YearMonth ym = YearMonth.from(t.date);
            accumulated.merge(t.categoryId, t.amountCents, Math::addExact);
            perMonth.merge(ym, t.amountCents, Math::addExact);
            if (ym.equals(m)) inMonth.merge(t.categoryId, t.amountCents, Math::addExact);
        }

        List<InvestmentCategoryDto> cats = new ArrayList<>();
        for (Category c : categories.list(userId)) {
            if (c.kind != Kind.INVESTIMENTO) continue;
            long acc = accumulated.getOrDefault(c.id, 0L);
            if (c.hidden && acc == 0) continue;
            cats.add(new InvestmentCategoryDto(c.id, c.name, c.icon, c.color, inMonth.getOrDefault(c.id, 0L), acc, c.goalCents));
        }
        List<MonthBarDto> history = new ArrayList<>();
        for (int i = HISTORY_MONTHS - 1; i >= 0; i--) {
            YearMonth ym = m.minusMonths(i);
            history.add(new MonthBarDto(ym.toString(), monthTag(ym), 0, 0, perMonth.getOrDefault(ym, 0L)));
        }
        long monthTotal = inMonth.values().stream().mapToLong(Long::longValue).sum();
        long accTotal = accumulated.values().stream().mapToLong(Long::longValue).sum();
        return new InvestmentsDto(m.toString(), monthTotal, accTotal, cats, history);
    }
}
