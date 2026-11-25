package br.com.fynavero.ledger;

import br.com.fynavero.ledger.IncomeSource.IncomeSourceRepository;

import br.com.fynavero.ledger.Recurrence.RecurrenceRepository;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.LedgerDtos.IncomeSourceDto;
import br.com.fynavero.ledger.LedgerDtos.IncomeSourceReq;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static br.com.fynavero.shared.Errors.notFound;

/**
 * Fontes de renda. Toda fonte com valor entra todo mês como receita Previsto no dia informado;
 * na renda variável o valor é uma estimativa, ajustada quando a pessoa recebe.
 */
@Service
public class IncomeSourceService {
    private static final int DEFAULT_DAY = 5;

    private final IncomeSourceRepository incomes;
    private final RecurrenceRepository recurrenceRepo;
    private final RecurrenceService recurrences;
    private final CategoryService categories;
    private final LedgerCalendar calendar;

    public IncomeSourceService(IncomeSourceRepository incomes, RecurrenceRepository recurrenceRepo,
                               RecurrenceService recurrences, CategoryService categories, LedgerCalendar calendar) {
        this.incomes = incomes;
        this.recurrenceRepo = recurrenceRepo;
        this.recurrences = recurrences;
        this.categories = categories;
        this.calendar = calendar;
    }

    public List<IncomeSourceDto> list(Long userId) {
        return incomes.findByUserIdOrderByCreatedAt(userId).stream().map(this::dto).toList();
    }

    private IncomeSourceDto dto(IncomeSource s) {
        Integer day = s.recurrenceId == null ? null
                : recurrenceRepo.findById(s.recurrenceId).map(r -> r.dayOfMonth).orElse(null);
        return new IncomeSourceDto(s.id, s.name, s.amountCents, s.fixed, s.categoryId, day);
    }

    /** Usa (ou cria) uma categoria de receita com o nome da fonte. */
    @Transactional
    public IncomeSourceDto create(Long userId, IncomeSourceReq req) {
        String name = req.name().strip();
        Category cat = categories.list(userId).stream()
                .filter(c -> c.kind == Kind.RECEITA && c.name.equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> categories.create(userId, name, Kind.RECEITA, null, "briefcase", null));
        IncomeSource s = new IncomeSource();
        s.userId = userId;
        s.name = name;
        s.amountCents = req.amountCents();
        s.fixed = req.fixed();
        s.categoryId = cat.id;
        s.createdAt = Instant.now();
        if (req.amountCents() > 0) s.recurrenceId = startRecurrence(userId, s, dayOf(req));
        return dto(incomes.save(s));
    }

    @Transactional
    public IncomeSourceDto update(Long userId, Long id, IncomeSourceReq req) {
        IncomeSource s = incomes.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        s.name = req.name().strip();
        s.amountCents = req.amountCents();
        s.fixed = req.fixed();
        int day = dayOf(req);
        boolean hasAmount = req.amountCents() > 0;
        if (s.recurrenceId != null && hasAmount) {
            recurrences.update(userId, s.recurrenceId,
                    new RecurrenceReq(Kind.RECEITA, s.categoryId, description(s), s.amountCents, day, null));
        } else if (s.recurrenceId != null) {
            recurrences.end(userId, s.recurrenceId);
            s.recurrenceId = null;
        } else if (hasAmount) {
            s.recurrenceId = startRecurrence(userId, s, day);
        }
        return dto(s);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        IncomeSource s = incomes.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        if (s.recurrenceId != null) recurrences.end(userId, s.recurrenceId);
        incomes.delete(s);
    }

    private Long startRecurrence(Long userId, IncomeSource s, int day) {
        return recurrences.create(userId, Kind.RECEITA, s.categoryId, description(s), s.amountCents, day, null,
                calendar.nextOccurrence(day), false).id;
    }

    private static int dayOf(IncomeSourceReq req) {
        return req.day() == null ? DEFAULT_DAY : req.day();
    }

    private static String description(IncomeSource s) {
        return s.fixed ? s.name : s.name + " (valor estimado)";
    }
}
