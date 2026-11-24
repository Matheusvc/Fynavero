package br.com.fynavero.ledger;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.domain.Kind;
import br.com.fynavero.domain.Origin;
import br.com.fynavero.domain.PaymentMethod;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;
import static br.com.fynavero.common.Texts.trimOrNull;

/**
 * Lançamentos que se repetem todo mês. As ocorrências viram lançamentos Previsto, gerados 12 meses à frente
 * e estendidos sob demanda quando alguém consulta meses mais distantes.
 */
@Service
public class RecurrenceService {
    private final RecurrenceRepository recurrences;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final LedgerCalendar calendar;

    public RecurrenceService(RecurrenceRepository recurrences, TransactionRepository transactions,
                             CategoryService categories, LedgerCalendar calendar) {
        this.recurrences = recurrences;
        this.transactions = transactions;
        this.categories = categories;
        this.calendar = calendar;
    }

    /** Recorrências ainda ativas (sem fim ou com fim a partir de hoje). */
    public List<Recurrence> active(Long userId) {
        LocalDate today = calendar.today();
        return recurrences.findByUserIdOrderByDayOfMonth(userId).stream()
                .filter(r -> r.endDate == null || !r.endDate.isBefore(today))
                .toList();
    }

    @Transactional
    public Recurrence create(Long userId, Kind kind, Long categoryId, String description, long amountCents,
                             int day, PaymentMethod pm, LocalDate start, boolean fixedBill) {
        Category c = categories.require(userId, categoryId);
        if (c.kind != kind) throw badRequest("A categoria não corresponde ao tipo do lançamento.");
        if (amountCents <= 0) throw badRequest("Informe um valor maior que zero.");
        Recurrence r = new Recurrence();
        r.userId = userId;
        r.kind = kind;
        r.categoryId = categoryId;
        r.description = trimOrNull(description);
        r.amountCents = amountCents;
        r.dayOfMonth = Math.max(1, Math.min(31, day));
        r.paymentMethod = pm;
        r.startDate = start;
        r.fixedBill = fixedBill;
        r.createdAt = Instant.now();
        recurrences.save(r);
        LocalDate horizon = calendar.horizon();
        generate(r, horizon.isAfter(start) ? horizon : YearMonth.from(start).atEndOfMonth());
        return r;
    }

    /** Conta fixa cadastrada em Configurações: começa no próximo vencimento. */
    @Transactional
    public Recurrence createFixedBill(Long userId, RecurrenceReq req) {
        return create(userId, req.kind(), req.categoryId(), req.description(), req.amountCents(), req.day(),
                req.paymentMethod(), calendar.nextOccurrence(req.day()), req.kind() == Kind.DESPESA);
    }

    /** Garante as ocorrências geradas até o fim do mês informado (no mínimo até o horizonte padrão). */
    @Transactional
    public void ensureGenerated(Long userId, YearMonth through) {
        LocalDate target = through.atEndOfMonth();
        if (target.isBefore(calendar.horizon())) target = calendar.horizon();
        if (target.isAfter(calendar.maxAhead())) target = calendar.maxAhead();
        for (Recurrence r : recurrences.findByUserIdOrderByDayOfMonth(userId)) {
            if (r.generatedThrough != null && !r.generatedThrough.isBefore(target)) continue;
            if (r.endDate != null && r.generatedThrough != null && !r.generatedThrough.isBefore(r.endDate)) continue;
            generate(r, target);
        }
    }

    private void generate(Recurrence r, LocalDate until) {
        YearMonth m = r.generatedThrough == null ? YearMonth.from(r.startDate) : YearMonth.from(r.generatedThrough).plusMonths(1);
        LocalDate limit = r.endDate != null && r.endDate.isBefore(until) ? r.endDate : until;
        List<Transaction> created = new ArrayList<>();
        for (; !m.atDay(1).isAfter(limit); m = m.plusMonths(1)) {
            LocalDate d = LedgerCalendar.dayIn(m, r.dayOfMonth);
            if (d.isBefore(r.startDate) || d.isAfter(limit)) continue;
            Transaction t = new Transaction();
            t.userId = r.userId;
            t.kind = r.kind;
            t.date = d;
            t.description = r.description;
            t.categoryId = r.categoryId;
            t.amountCents = r.amountCents;
            t.paymentMethod = r.paymentMethod;
            t.status = calendar.statusFor(d);
            t.recurrenceId = r.id;
            t.origin = Origin.MANUAL;
            t.createdAt = Instant.now();
            created.add(t);
        }
        transactions.saveAll(created);
        // Nunca volta atrás: se a recorrência for reaberta, a geração continua de onde parou.
        if (r.generatedThrough == null || limit.isAfter(r.generatedThrough)) r.generatedThrough = limit;
        recurrences.save(r);
    }

    /** Altera o modelo e todas as ocorrências a partir de hoje. */
    @Transactional
    public Recurrence update(Long userId, Long id, RecurrenceReq req) {
        Recurrence r = require(userId, id);
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != r.kind) throw badRequest("A categoria não corresponde ao tipo.");
        r.categoryId = c.id;
        r.description = trimOrNull(req.description());
        r.amountCents = req.amountCents();
        r.dayOfMonth = req.day();
        r.paymentMethod = req.paymentMethod();
        for (Transaction t : transactions.findRecurrenceFrom(userId, r.id, calendar.today())) {
            applyTemplate(t, r);
            t.date = LedgerCalendar.dayIn(YearMonth.from(t.date), r.dayOfMonth);
            t.status = calendar.statusFor(t.date);
        }
        return r;
    }

    /** Encerra a recorrência: remove as ocorrências a partir de amanhã. */
    @Transactional
    public void end(Long userId, Long id) {
        Recurrence r = require(userId, id);
        r.endDate = calendar.today();
        Instant now = Instant.now();
        for (Transaction t : transactions.findRecurrenceFrom(userId, r.id, calendar.today().plusDays(1))) t.deletedAt = now;
    }

    /** Ao desfazer a exclusão de ocorrências, reabre a recorrência que tinha sido encerrada antes delas. */
    @Transactional
    public void reopenIfEndedBefore(Long userId, Long id, LocalDate date) {
        recurrences.findByIdAndUserId(id, userId).ifPresent(r -> {
            if (r.endDate != null && date.isAfter(r.endDate)) r.endDate = null;
        });
    }

    Recurrence require(Long userId, Long id) {
        return recurrences.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
    }

    static void applyTemplate(Transaction t, Recurrence r) {
        t.amountCents = r.amountCents;
        t.categoryId = r.categoryId;
        t.description = r.description;
        t.paymentMethod = r.paymentMethod;
    }
}
