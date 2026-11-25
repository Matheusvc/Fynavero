package br.com.fynavero.planning;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryDtos.CategoryReq;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.LedgerDtos.TxReq;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionRepository;
import br.com.fynavero.ledger.TransactionService;
import br.com.fynavero.planning.PlanningDtos.GoalDto;
import br.com.fynavero.planning.PlanningDtos.GoalReq;
import br.com.fynavero.planning.PlanningDtos.GoalStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;

/**
 * Objetivos de economia (viagem, reserva, carro…). Cada objetivo é uma categoria de investimento com meta e
 * prazo: guardar dinheiro para ele é lançar um investimento nessa categoria, e o progresso é a soma guardada.
 */
@Service
public class GoalService {
    private final CategoryService categories;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final LedgerCalendar calendar;

    public GoalService(CategoryService categories, TransactionRepository transactions,
                       TransactionService transactionService, LedgerCalendar calendar) {
        this.categories = categories;
        this.transactions = transactions;
        this.transactionService = transactionService;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<GoalDto> list(Long userId) {
        LocalDate today = calendar.today();
        YearMonth month = YearMonth.from(today);
        Map<Long, Long> saved = new HashMap<>();
        Map<Long, Long> savedThisMonth = new HashMap<>();
        for (Transaction t : transactions.findKindUpTo(userId, Kind.INVESTIMENTO, today)) {
            saved.merge(t.categoryId, t.amountCents, Math::addExact);
            if (YearMonth.from(t.date).equals(month)) savedThisMonth.merge(t.categoryId, t.amountCents, Math::addExact);
        }
        return categories.list(userId).stream()
                .filter(GoalService::isGoal)
                .map(c -> toDto(c, saved.getOrDefault(c.id, 0L), savedThisMonth.getOrDefault(c.id, 0L), today))
                .sorted((a, b) -> {
                    if (a.deadline() == null) return b.deadline() == null ? a.name().compareToIgnoreCase(b.name()) : 1;
                    if (b.deadline() == null) return -1;
                    return a.deadline().compareTo(b.deadline());
                })
                .toList();
    }

    @Transactional
    public GoalDto create(Long userId, GoalReq req) {
        validateDeadline(req.deadline());
        String name = req.name().strip();
        Category c = categories.list(userId).stream()
                .filter(x -> x.kind == Kind.INVESTIMENTO && x.name.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (c != null && isGoal(c)) throw badRequest("Você já tem um objetivo com esse nome.");
        if (c == null) {
            c = categories.create(userId, name, Kind.INVESTIMENTO, null,
                    req.icon() == null || req.icon().isBlank() ? "target" : req.icon(), req.targetCents());
        } else {
            c.goalCents = req.targetCents();
            c.hidden = false;
            if (req.icon() != null && !req.icon().isBlank()) c.icon = req.icon();
        }
        c.goalDeadline = req.deadline();
        if (req.initialCents() != null && req.initialCents() > 0) {
            transactionService.create(userId, new TxReq(Kind.INVESTIMENTO, req.initialCents(), c.id, calendar.today(),
                    "Valor já guardado", null, null, false, null, null, null, null));
        }
        Long id = c.id;
        return list(userId).stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public GoalDto update(Long userId, Long id, GoalReq req) {
        Category c = requireGoal(userId, id);
        if (req.deadline() != null && !req.deadline().equals(c.goalDeadline)) validateDeadline(req.deadline());
        categories.update(userId, id, new CategoryReq(req.name(), null, null, req.icon(), null, req.targetCents()));
        c.goalDeadline = req.deadline();
        return list(userId).stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow();
    }

    /** Deixa de ser objetivo; a categoria e o que já foi guardado continuam em Investimentos. */
    @Transactional
    public void remove(Long userId, Long id) {
        Category c = requireGoal(userId, id);
        c.goalCents = null;
        c.goalDeadline = null;
    }

    private Category requireGoal(Long userId, Long id) {
        Category c = categories.require(userId, id);
        if (!isGoal(c)) throw notFound();
        return c;
    }

    private void validateDeadline(LocalDate deadline) {
        if (deadline != null && deadline.isBefore(calendar.today())) throw badRequest("O prazo precisa ser hoje ou depois.");
    }

    static boolean isGoal(Category c) {
        return c.kind == Kind.INVESTIMENTO && c.goalCents != null && c.goalCents > 0;
    }

    static GoalDto toDto(Category c, long saved, long savedThisMonth, LocalDate today) {
        long target = c.goalCents;
        long remaining = Math.max(0, target - saved);
        int pct = (int) Math.min(10_000, saved * 10_000 / target);
        Integer monthsLeft = null;
        Long monthlyNeeded = null;
        GoalStatus status;
        if (remaining == 0) {
            status = GoalStatus.CONCLUIDO;
        } else if (c.goalDeadline == null) {
            status = GoalStatus.SEM_PRAZO;
        } else if (c.goalDeadline.isBefore(today)) {
            status = GoalStatus.PRAZO_VENCIDO;
            monthsLeft = 0;
        } else {
            monthsLeft = (int) ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(c.goalDeadline)) + 1;
            // O que guardar neste mês é calculado sobre o que faltava no início do mês.
            long remainingAtMonthStart = Math.max(0, target - (saved - savedThisMonth));
            monthlyNeeded = (remainingAtMonthStart + monthsLeft - 1) / monthsLeft;
            status = savedThisMonth >= monthlyNeeded ? GoalStatus.NO_RITMO : GoalStatus.ATENCAO;
        }
        return new GoalDto(c.id, c.name, c.icon, c.color, target, saved, remaining, pct, c.goalDeadline, monthsLeft,
                monthlyNeeded, savedThisMonth, status);
    }
}
