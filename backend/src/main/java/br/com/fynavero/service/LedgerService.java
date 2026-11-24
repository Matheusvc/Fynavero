package br.com.fynavero.service;

import br.com.fynavero.api.Dto.InstallmentConvertReq;
import br.com.fynavero.api.Dto.RecurrenceReq;
import br.com.fynavero.api.Dto.TxReq;
import br.com.fynavero.api.Dto.TxUpdateReq;
import br.com.fynavero.calc.FinanceCalculator;
import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.*;
import br.com.fynavero.domain.Repositories.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.*;
import java.util.*;

import static br.com.fynavero.support.Support.*;

/** Lançamentos, recorrências e parcelamentos. */
@Service
public class LedgerService {
    private static final int HORIZON_MONTHS = 12;
    private static final int MAX_AHEAD_MONTHS = 60;

    private final TransactionRepo txs;
    private final RecurrenceRepo recs;
    private final InstallmentPlanRepo plans;
    private final CategoryService categories;
    private final Clock clock;

    public LedgerService(TransactionRepo txs, RecurrenceRepo recs, InstallmentPlanRepo plans,
                         CategoryService categories, Clock clock) {
        this.txs = txs;
        this.recs = recs;
        this.plans = plans;
        this.categories = categories;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private Status statusFor(LocalDate date) {
        return date.isAfter(today()) ? Status.PREVISTO : Status.REALIZADO;
    }

    private LocalDate horizon() {
        return YearMonth.now(clock).plusMonths(HORIZON_MONTHS).atEndOfMonth();
    }

    // ------------------------------------------------------------------ leitura

    @Transactional
    public List<Transaction> range(Long userId, LocalDate from, LocalDate to) {
        ensureGenerated(userId, YearMonth.from(to));
        return txs.findRange(userId, from, to);
    }

    @Transactional
    public List<Transaction> month(Long userId, YearMonth m) {
        return range(userId, m.atDay(1), m.atEndOfMonth());
    }

    public List<Transaction> filter(List<Transaction> list, String q, Long categoryId, Kind kind, PaymentMethod pm,
                                    Map<Long, Category> cats) {
        String needle = q == null || q.isBlank() ? null : fold(q);
        return list.stream()
                .filter(t -> categoryId == null || categoryId.equals(t.categoryId))
                .filter(t -> kind == null || kind == t.kind)
                .filter(t -> pm == null || pm == t.paymentMethod)
                .filter(t -> needle == null
                        || (t.description != null && fold(t.description).contains(needle))
                        || (t.note != null && fold(t.note).contains(needle))
                        || (cats.containsKey(t.categoryId) && fold(cats.get(t.categoryId).name).contains(needle)))
                .toList();
    }

    public Transaction require(Long userId, Long id) {
        return txs.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound());
    }

    // ------------------------------------------------------------------ recorrências

    @Transactional
    public Recurrence createRecurrence(Long userId, Kind kind, Long categoryId, String description, long amountCents,
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
        recs.save(r);
        LocalDate until = horizon().isAfter(start) ? horizon() : YearMonth.from(start).atEndOfMonth();
        generate(r, until);
        return r;
    }

    /** Primeira ocorrência de uma conta fixa: o dia de vencimento deste mês, ou do próximo se já passou. */
    public LocalDate nextOccurrence(int day) {
        LocalDate today = today();
        YearMonth m = YearMonth.from(today);
        LocalDate d = m.atDay(Math.min(day, m.lengthOfMonth()));
        if (d.isBefore(today)) {
            m = m.plusMonths(1);
            d = m.atDay(Math.min(day, m.lengthOfMonth()));
        }
        return d;
    }

    @Transactional
    public void ensureGenerated(Long userId, YearMonth through) {
        LocalDate target = through.atEndOfMonth();
        if (target.isBefore(horizon())) target = horizon();
        LocalDate cap = YearMonth.now(clock).plusMonths(MAX_AHEAD_MONTHS).atEndOfMonth();
        if (target.isAfter(cap)) target = cap;
        for (Recurrence r : recs.findByUserIdOrderByDayOfMonth(userId)) {
            if (r.generatedThrough != null && !r.generatedThrough.isBefore(target)) continue;
            if (r.endDate != null && r.generatedThrough != null && !r.generatedThrough.isBefore(r.endDate)) continue;
            generate(r, target);
        }
    }

    private void generate(Recurrence r, LocalDate until) {
        YearMonth m = r.generatedThrough == null
                ? YearMonth.from(r.startDate)
                : YearMonth.from(r.generatedThrough).plusMonths(1);
        LocalDate limit = r.endDate != null && r.endDate.isBefore(until) ? r.endDate : until;
        List<Transaction> created = new ArrayList<>();
        while (!m.atDay(1).isAfter(limit)) {
            LocalDate d = m.atDay(Math.min(r.dayOfMonth, m.lengthOfMonth()));
            if (!d.isBefore(r.startDate) && !d.isAfter(limit)) {
                Transaction t = new Transaction();
                t.userId = r.userId;
                t.kind = r.kind;
                t.date = d;
                t.description = r.description;
                t.categoryId = r.categoryId;
                t.amountCents = r.amountCents;
                t.paymentMethod = r.paymentMethod;
                t.status = statusFor(d);
                t.recurrenceId = r.id;
                t.origin = Origin.MANUAL;
                t.createdAt = Instant.now();
                created.add(t);
            }
            m = m.plusMonths(1);
        }
        txs.saveAll(created);
        if (r.generatedThrough == null || limit.isAfter(r.generatedThrough)) r.generatedThrough = limit;
        recs.save(r);
    }

    public List<Recurrence> recurrences(Long userId) {
        return recs.findByUserIdOrderByDayOfMonth(userId).stream()
                .filter(r -> r.endDate == null || !r.endDate.isBefore(today()))
                .toList();
    }

    @Transactional
    public Recurrence createFixedBill(Long userId, RecurrenceReq req) {
        return createRecurrence(userId, req.kind(), req.categoryId(), req.description(), req.amountCents(),
                req.day(), req.paymentMethod(), nextOccurrence(req.day()), req.kind() == Kind.DESPESA);
    }

    /** Altera o modelo e todas as ocorrências futuras (a partir de hoje). */
    @Transactional
    public Recurrence updateRecurrence(Long userId, Long id, RecurrenceReq req) {
        Recurrence r = recs.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != r.kind) throw badRequest("A categoria não corresponde ao tipo.");
        r.categoryId = c.id;
        r.description = trimOrNull(req.description());
        r.amountCents = req.amountCents();
        r.dayOfMonth = req.day();
        r.paymentMethod = req.paymentMethod();
        for (Transaction t : txs.findRecurrenceFrom(userId, r.id, today())) {
            applyTemplate(t, r);
            YearMonth m = YearMonth.from(t.date);
            t.date = m.atDay(Math.min(r.dayOfMonth, m.lengthOfMonth()));
            t.status = statusFor(t.date);
        }
        return r;
    }

    /** Encerra a recorrência: remove as ocorrências a partir de amanhã. */
    @Transactional
    public void endRecurrence(Long userId, Long id) {
        Recurrence r = recs.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        r.endDate = today();
        Instant now = Instant.now();
        for (Transaction t : txs.findRecurrenceFrom(userId, r.id, today().plusDays(1))) t.deletedAt = now;
    }

    // ------------------------------------------------------------------ criação

    @Transactional
    public List<Transaction> create(Long userId, TxReq req) {
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != req.kind()) throw badRequest("A categoria escolhida não é do tipo deste lançamento.");
        LocalDate date = req.date() != null ? req.date() : today();
        String description = trimOrNull(req.description());
        String note = trimOrNull(req.note());

        if (req.installments() != null) {
            if (req.kind() != Kind.DESPESA) throw badRequest("Só despesas podem ser parceladas.");
            if (req.repeatMonthly()) throw badRequest("Escolha entre compra parcelada ou repetir todo mês.");
            long[] parts = parts(req.amountCents(), req.installments(), Boolean.TRUE.equals(req.installmentValue()));
            return createInstallments(userId, c.id, description, parts, date, req.paymentMethod(), note);
        }
        if (req.loan() != null) {
            if (req.kind() != Kind.RECEITA || !c.loan) throw badRequest("O pagamento em parcelas é só para empréstimo recebido.");
            if (req.repeatMonthly()) throw badRequest("Um empréstimo não se repete todo mês.");
            Transaction received = single(userId, req.kind(), c.id, date, description, req.amountCents(),
                    req.paymentMethod(), note);
            Category payCat = req.loan().categoryId() != null
                    ? categories.require(userId, req.loan().categoryId())
                    : categories.ensureLoanPaymentCategory(userId);
            if (payCat.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa para as parcelas.");
            LocalDate first = req.loan().firstDate() != null ? req.loan().firstDate() : date.plusMonths(1);
            String payDesc = description != null ? "Pagamento: " + description : "Pagamento do empréstimo";
            List<Transaction> out = new ArrayList<>(List.of(received));
            out.addAll(createInstallments(userId, payCat.id, payDesc,
                    parts(req.loan().installmentCents(), req.loan().count(), true), first, null, null));
            return out;
        }
        if (req.repeatMonthly()) {
            Recurrence r = createRecurrence(userId, req.kind(), c.id, description, req.amountCents(),
                    date.getDayOfMonth(), req.paymentMethod(), date, false);
            List<Transaction> list = txs.findRecurrenceFrom(userId, r.id, date);
            if (note != null && !list.isEmpty()) list.getFirst().note = note;
            return list;
        }
        return List.of(single(userId, req.kind(), c.id, date, description, req.amountCents(), req.paymentMethod(), note));
    }

    private Transaction single(Long userId, Kind kind, Long categoryId, LocalDate date, String description,
                               long amountCents, PaymentMethod pm, String note) {
        Transaction t = new Transaction();
        t.userId = userId;
        t.kind = kind;
        t.date = date;
        t.description = description;
        t.categoryId = categoryId;
        t.amountCents = amountCents;
        t.paymentMethod = pm;
        t.status = statusFor(date);
        t.origin = Origin.MANUAL;
        t.note = note;
        t.createdAt = Instant.now();
        return txs.save(t);
    }

    /** Valores das parcelas: total dividido igualmente, ou valor fixo por parcela. */
    private static long[] parts(long amountCents, int count, boolean perInstallment) {
        try {
            return perInstallment
                    ? FinanceCalculator.fixedInstallments(amountCents, count)
                    : FinanceCalculator.splitInstallments(amountCents, count);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw badRequest(e instanceof ArithmeticException ? "Valor alto demais." : e.getMessage() + ".");
        }
    }

    @Transactional
    public List<Transaction> createInstallments(Long userId, Long categoryId, String description, long totalCents,
                                                int count, LocalDate first, PaymentMethod pm, String note) {
        return createInstallments(userId, categoryId, description, parts(totalCents, count, false), first, pm, note);
    }

    @Transactional
    public List<Transaction> createInstallments(Long userId, Long categoryId, String description, long[] parts,
                                                LocalDate first, PaymentMethod pm, String note) {
        int count = parts.length;
        long totalCents = 0;
        for (long v : parts) totalCents = Math.addExact(totalCents, v);
        InstallmentPlan p = new InstallmentPlan();
        p.userId = userId;
        p.categoryId = categoryId;
        p.description = description;
        p.totalCents = totalCents;
        p.installmentCount = count;
        p.firstDate = first;
        p.paymentMethod = pm;
        p.createdAt = Instant.now();
        plans.save(p);
        List<Transaction> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Transaction t = new Transaction();
            t.userId = userId;
            t.kind = Kind.DESPESA;
            t.date = first.plusMonths(i);
            t.description = description;
            t.categoryId = categoryId;
            t.amountCents = parts[i];
            t.paymentMethod = pm;
            t.status = statusFor(t.date);
            t.installmentNumber = i + 1;
            t.installmentTotal = count;
            t.installmentPlanId = p.id;
            t.origin = Origin.MANUAL;
            t.note = i == 0 ? note : null;
            t.createdAt = Instant.now();
            list.add(t);
        }
        return txs.saveAll(list);
    }

    /** Transforma um lançamento simples de despesa em compra parcelada a partir da mesma data. */
    @Transactional
    public List<Transaction> convertToInstallments(Long userId, Long id, InstallmentConvertReq req) {
        Transaction t = require(userId, id);
        if (t.kind != Kind.DESPESA) throw badRequest("Só despesas podem ser parceladas.");
        if (t.installmentPlanId != null) throw badRequest("Este lançamento já é parcelado.");
        if (t.recurrenceId != null) throw badRequest("Lançamentos que se repetem todo mês não podem ser parcelados.");
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa.");
        List<Transaction> created = createInstallments(userId, c.id, trimOrNull(req.description()),
                parts(req.amountCents(), req.count(), req.installmentValue()), req.date(), req.paymentMethod(),
                trimOrNull(req.note()));
        txs.delete(t);
        return created;
    }

    /**
     * Muda o número total de parcelas. Parcelas novas repetem o valor da parcela comum e entram nos meses seguintes;
     * reduzir remove as últimas, sem apagar parcelas já vencidas.
     */
    @Transactional
    public List<Transaction> resizePlan(Long userId, Long planId, int newCount) {
        InstallmentPlan p = plans.findByIdAndUserId(planId, userId).orElseThrow(() -> notFound());
        List<Transaction> parcels = new ArrayList<>(txs.findByPlan(userId, planId));
        if (parcels.isEmpty()) throw notFound();
        parcels.sort(Comparator.comparing(x -> x.installmentNumber));
        long paid = parcels.stream().filter(x -> !x.date.isAfter(today())).count();
        if (newCount < Math.max(1, paid)) {
            throw badRequest("Já " + (paid == 1 ? "venceu 1 parcela" : "venceram " + paid + " parcelas") + ". Use pelo menos " + paid + ".");
        }
        Transaction last = parcels.getLast();
        long regular = parcels.getFirst().amountCents;
        if (newCount < parcels.size()) {
            List<Transaction> drop = parcels.subList(newCount, parcels.size());
            txs.deleteAll(new ArrayList<>(drop));
            drop.clear();
        } else {
            for (int n = parcels.size() + 1; n <= newCount; n++) {
                Transaction t = last.copy();
                t.installmentNumber = n;
                t.date = p.firstDate.plusMonths(n - 1L);
                t.amountCents = regular;
                t.status = statusFor(t.date);
                t.note = null;
                parcels.add(t);
            }
        }
        for (Transaction t : parcels) t.installmentTotal = newCount;
        txs.saveAll(parcels);
        p.installmentCount = newCount;
        p.totalCents = parcels.stream().mapToLong(x -> x.amountCents).sum();
        return parcels;
    }

    // ------------------------------------------------------------------ edição

    @Transactional
    public Transaction update(Long userId, Long id, Scope scope, TxUpdateReq req) {
        Transaction t = require(userId, id);
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != t.kind) throw badRequest("A categoria escolhida não é do tipo deste lançamento.");
        LocalDate oldDate = t.date;
        t.amountCents = req.amountCents();
        t.categoryId = c.id;
        t.date = req.date();
        t.description = trimOrNull(req.description());
        t.paymentMethod = req.paymentMethod();
        t.note = trimOrNull(req.note());
        if (req.status() != null) t.status = req.status();

        if (scope == Scope.FOLLOWING && t.installmentPlanId != null) {
            for (Transaction o : txs.findByPlan(userId, t.installmentPlanId)) {
                if (o.installmentNumber != null && o.installmentNumber > t.installmentNumber) {
                    o.amountCents = t.amountCents;
                    o.categoryId = t.categoryId;
                    o.description = t.description;
                    o.paymentMethod = t.paymentMethod;
                }
            }
            plans.findByIdAndUserId(t.installmentPlanId, userId).ifPresent(p -> {
                p.categoryId = t.categoryId;
                p.description = t.description;
                p.totalCents = txs.findByPlan(userId, p.id).stream().mapToLong(x -> x.amountCents).sum();
            });
        } else if (scope == Scope.FOLLOWING && t.recurrenceId != null) {
            Recurrence r = recs.findByIdAndUserId(t.recurrenceId, userId).orElseThrow(() -> notFound());
            r.amountCents = t.amountCents;
            r.categoryId = t.categoryId;
            r.description = t.description;
            r.paymentMethod = t.paymentMethod;
            r.dayOfMonth = t.date.getDayOfMonth();
            for (Transaction o : txs.findRecurrenceFrom(userId, r.id, oldDate.plusDays(1))) {
                if (o.id.equals(t.id)) continue;
                applyTemplate(o, r);
                YearMonth m = YearMonth.from(o.date);
                o.date = m.atDay(Math.min(r.dayOfMonth, m.lengthOfMonth()));
            }
        }
        return t;
    }

    private static void applyTemplate(Transaction t, Recurrence r) {
        t.amountCents = r.amountCents;
        t.categoryId = r.categoryId;
        t.description = r.description;
        t.paymentMethod = r.paymentMethod;
    }

    @Transactional
    public Transaction markPaid(Long userId, Long id) {
        Transaction t = require(userId, id);
        t.status = Status.REALIZADO;
        return t;
    }

    @Transactional
    public Transaction duplicate(Long userId, Long id) {
        Transaction src = require(userId, id);
        Transaction t = src.copy();
        t.date = today();
        t.status = Status.REALIZADO;
        t.installmentNumber = null;
        t.installmentTotal = null;
        t.installmentPlanId = null;
        t.recurrenceId = null;
        t.importBatchId = null;
        t.origin = Origin.MANUAL;
        return txs.save(t);
    }

    // ------------------------------------------------------------------ exclusão reversível

    @Transactional
    public List<Long> delete(Long userId, Long id, Scope scope) {
        Transaction t = require(userId, id);
        List<Transaction> targets = new ArrayList<>(List.of(t));
        if (scope == Scope.FOLLOWING && t.installmentPlanId != null) {
            txs.findByPlan(userId, t.installmentPlanId).stream()
                    .filter(o -> o.installmentNumber != null && o.installmentNumber > t.installmentNumber)
                    .forEach(targets::add);
        } else if (scope == Scope.FOLLOWING && t.recurrenceId != null) {
            Recurrence r = recs.findByIdAndUserId(t.recurrenceId, userId).orElseThrow(() -> notFound());
            r.endDate = t.date.minusDays(1);
            txs.findRecurrenceFrom(userId, r.id, t.date.plusDays(1)).forEach(targets::add);
        }
        Instant now = Instant.now();
        targets.forEach(x -> x.deletedAt = now);
        return targets.stream().map(x -> x.id).toList();
    }

    @Transactional
    public void restore(Long userId, List<Long> ids) {
        for (Transaction t : txs.findByIdInAndUserId(ids, userId)) {
            if (t.deletedAt == null) continue;
            t.deletedAt = null;
            if (t.recurrenceId != null) {
                recs.findByIdAndUserId(t.recurrenceId, userId).ifPresent(r -> {
                    if (r.endDate != null && t.date.isAfter(r.endDate)) r.endDate = null;
                });
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeDeleted() {
        txs.purgeAllDeleted(Instant.now().minus(Duration.ofDays(1)));
    }

    public static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
}
