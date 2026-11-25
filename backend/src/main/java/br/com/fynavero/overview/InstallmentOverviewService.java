package br.com.fynavero.overview;

import br.com.fynavero.ledger.Kind;
import br.com.fynavero.ledger.InstallmentPlan;
import br.com.fynavero.ledger.InstallmentPlanRepository;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.RecurrenceRepository;
import br.com.fynavero.ledger.RecurrenceService;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.TransactionRepository;
import br.com.fynavero.overview.OverviewDtos.CommitmentDto;
import br.com.fynavero.overview.OverviewDtos.InstallmentsDto;
import br.com.fynavero.overview.OverviewDtos.PlanDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static br.com.fynavero.shared.Formats.monthName;
import static br.com.fynavero.shared.Formats.monthTag;
import static br.com.fynavero.overview.LedgerFigures.byMonth;

/** Tela Parcelas e Dívidas: cada parcelamento com progresso e a linha do tempo dos próximos 12 meses. */
@Service
public class InstallmentOverviewService {
    private static final int TIMELINE_MONTHS = 12;

    private final InstallmentPlanRepository plans;
    private final TransactionRepository transactions;
    private final RecurrenceRepository recurrenceRepo;
    private final RecurrenceService recurrences;
    private final LedgerCalendar calendar;

    public InstallmentOverviewService(InstallmentPlanRepository plans, TransactionRepository transactions,
                                      RecurrenceRepository recurrenceRepo, RecurrenceService recurrences,
                                      LedgerCalendar calendar) {
        this.plans = plans;
        this.transactions = transactions;
        this.recurrenceRepo = recurrenceRepo;
        this.recurrences = recurrences;
        this.calendar = calendar;
    }

    @Transactional
    public InstallmentsDto overview(Long userId, YearMonth m) {
        recurrences.ensureGenerated(userId, m.plusMonths(TIMELINE_MONTHS - 1));
        List<PlanDto> list = plansOf(userId, m);
        List<CommitmentDto> timeline = timeline(userId, m);
        return new InstallmentsDto(list, timeline, timeline.getFirst().installments());
    }

    private List<PlanDto> plansOf(Long userId, YearMonth m) {
        LocalDate today = calendar.today();
        Map<Long, List<Transaction>> byPlan = transactions.findAllInstallments(userId).stream()
                .collect(Collectors.groupingBy(t -> t.installmentPlanId));
        List<PlanDto> out = new ArrayList<>();
        for (InstallmentPlan p : plans.findByUserIdOrderByFirstDateDesc(userId)) {
            List<Transaction> parcels = byPlan.get(p.id);
            if (parcels == null || parcels.isEmpty()) continue;
            parcels.sort(Comparator.comparing(t -> t.installmentNumber));
            int paid = (int) parcels.stream().filter(t -> !t.date.isAfter(today)).count();
            int current = (int) parcels.stream().filter(t -> !YearMonth.from(t.date).isAfter(m)).count();
            long remaining = parcels.stream().filter(t -> t.date.isAfter(today)).mapToLong(t -> t.amountCents).sum();
            long total = parcels.stream().mapToLong(t -> t.amountCents).sum();
            LocalDate last = parcels.getLast().date;
            out.add(new PlanDto(p.id, p.description, p.categoryId, parcels.getFirst().amountCents, total,
                    parcels.size(), paid, current, remaining, monthName(YearMonth.from(last)) + " " + last.getYear(),
                    p.firstDate, remaining == 0));
        }
        out.sort(Comparator.comparing(PlanDto::finished).thenComparing(PlanDto::firstDate, Comparator.reverseOrder()));
        return out;
    }

    /** Total comprometido por mês: parcelas + contas fixas de despesa. */
    private List<CommitmentDto> timeline(Long userId, YearMonth m) {
        Set<Long> expenseRecurrences = recurrenceRepo.findByUserIdOrderByDayOfMonth(userId).stream()
                .filter(r -> r.kind == Kind.DESPESA).map(r -> r.id).collect(Collectors.toSet());
        Map<YearMonth, List<Transaction>> months = byMonth(
                transactions.findRange(userId, m.atDay(1), m.plusMonths(TIMELINE_MONTHS - 1).atEndOfMonth()));
        List<CommitmentDto> out = new ArrayList<>();
        for (int i = 0; i < TIMELINE_MONTHS; i++) {
            YearMonth ym = m.plusMonths(i);
            List<Transaction> list = months.getOrDefault(ym, List.of());
            long inst = list.stream().filter(t -> t.installmentPlanId != null).mapToLong(t -> t.amountCents).sum();
            long fixed = list.stream().filter(t -> t.recurrenceId != null && expenseRecurrences.contains(t.recurrenceId))
                    .mapToLong(t -> t.amountCents).sum();
            out.add(new CommitmentDto(ym.toString(), monthTag(ym), inst, fixed));
        }
        return out;
    }
}
