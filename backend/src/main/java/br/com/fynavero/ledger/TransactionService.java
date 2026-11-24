package br.com.fynavero.ledger;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.domain.Kind;
import br.com.fynavero.domain.Origin;
import br.com.fynavero.domain.PaymentMethod;
import br.com.fynavero.domain.Scope;
import br.com.fynavero.domain.Status;
import br.com.fynavero.ledger.LedgerDtos.InstallmentConvertReq;
import br.com.fynavero.ledger.LedgerDtos.LoanRepayment;
import br.com.fynavero.ledger.LedgerDtos.TxReq;
import br.com.fynavero.ledger.LedgerDtos.TxUpdateReq;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;
import static br.com.fynavero.common.Texts.fold;
import static br.com.fynavero.common.Texts.trimOrNull;

/** Lançamentos: consulta, criação (simples, recorrente, parcelada, empréstimo), edição e exclusão reversível. */
@Service
public class TransactionService {
    private final TransactionRepository transactions;
    private final RecurrenceService recurrences;
    private final InstallmentService installments;
    private final CategoryService categories;
    private final LedgerCalendar calendar;

    public TransactionService(TransactionRepository transactions, RecurrenceService recurrences,
                              InstallmentService installments, CategoryService categories, LedgerCalendar calendar) {
        this.transactions = transactions;
        this.recurrences = recurrences;
        this.installments = installments;
        this.categories = categories;
        this.calendar = calendar;
    }

    // ------------------------------------------------------------------ consulta

    /** Lançamentos do período, garantindo antes as ocorrências das recorrências. */
    @Transactional
    public List<Transaction> range(Long userId, LocalDate from, LocalDate to) {
        recurrences.ensureGenerated(userId, YearMonth.from(to));
        return transactions.findRange(userId, from, to);
    }

    @Transactional
    public List<Transaction> month(Long userId, YearMonth m) {
        return range(userId, m.atDay(1), m.atEndOfMonth());
    }

    /** Busca por descrição, observação ou nome da categoria (sem acentos) e filtros da tela de Lançamentos. */
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
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound());
    }

    // ------------------------------------------------------------------ criação

    @Transactional
    public List<Transaction> create(Long userId, TxReq req) {
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != req.kind()) throw badRequest("A categoria escolhida não é do tipo deste lançamento.");
        LocalDate date = req.date() != null ? req.date() : calendar.today();
        String description = trimOrNull(req.description());
        String note = trimOrNull(req.note());

        if (req.installments() != null) {
            if (req.kind() != Kind.DESPESA) throw badRequest("Só despesas podem ser parceladas.");
            if (req.repeatMonthly()) throw badRequest("Escolha entre compra parcelada ou repetir todo mês.");
            long[] parts = InstallmentService.parts(req.amountCents(), req.installments(),
                    Boolean.TRUE.equals(req.installmentValue()));
            return installments.create(userId, c.id, description, parts, date, req.paymentMethod(), note);
        }
        if (req.loan() != null) {
            if (req.kind() != Kind.RECEITA || !c.loan) throw badRequest("O pagamento em parcelas é só para empréstimo recebido.");
            if (req.repeatMonthly()) throw badRequest("Um empréstimo não se repete todo mês.");
            Transaction received = single(userId, req.kind(), c.id, date, description, req.amountCents(), req.paymentMethod(), note);
            List<Transaction> out = new ArrayList<>(List.of(received));
            out.addAll(createLoanRepayment(userId, req.loan(), date, description));
            return out;
        }
        if (req.repeatMonthly()) {
            Recurrence r = recurrences.create(userId, req.kind(), c.id, description, req.amountCents(),
                    date.getDayOfMonth(), req.paymentMethod(), date, false);
            List<Transaction> list = transactions.findRecurrenceFrom(userId, r.id, date);
            if (note != null && !list.isEmpty()) list.getFirst().note = note;
            return list;
        }
        return List.of(single(userId, req.kind(), c.id, date, description, req.amountCents(), req.paymentMethod(), note));
    }

    /** Parcelas de pagamento de um empréstimo, como despesa (padrão: "Empréstimos e financiamentos"). */
    private List<Transaction> createLoanRepayment(Long userId, LoanRepayment loan, LocalDate received, String description) {
        Category payCat = loan.categoryId() != null
                ? categories.require(userId, loan.categoryId())
                : categories.ensureLoanPaymentCategory(userId);
        if (payCat.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa para as parcelas.");
        LocalDate first = loan.firstDate() != null ? loan.firstDate() : received.plusMonths(1);
        String payDesc = description != null ? "Pagamento: " + description : "Pagamento do empréstimo";
        return installments.create(userId, payCat.id, payDesc,
                InstallmentService.parts(loan.installmentCents(), loan.count(), true), first, null, null);
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
        t.status = calendar.statusFor(date);
        t.origin = Origin.MANUAL;
        t.note = note;
        t.createdAt = Instant.now();
        return transactions.save(t);
    }

    @Transactional
    public List<Transaction> convertToInstallments(Long userId, Long id, InstallmentConvertReq req) {
        return installments.convert(require(userId, id), userId, req);
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
            installments.applyToFollowing(userId, t);
        } else if (scope == Scope.FOLLOWING && t.recurrenceId != null) {
            Recurrence r = recurrences.require(userId, t.recurrenceId);
            r.amountCents = t.amountCents;
            r.categoryId = t.categoryId;
            r.description = t.description;
            r.paymentMethod = t.paymentMethod;
            r.dayOfMonth = t.date.getDayOfMonth();
            for (Transaction o : transactions.findRecurrenceFrom(userId, r.id, oldDate.plusDays(1))) {
                if (o.id.equals(t.id)) continue;
                RecurrenceService.applyTemplate(o, r);
                o.date = LedgerCalendar.dayIn(YearMonth.from(o.date), r.dayOfMonth);
            }
        }
        return t;
    }

    @Transactional
    public Transaction markPaid(Long userId, Long id) {
        Transaction t = require(userId, id);
        t.status = Status.REALIZADO;
        return t;
    }

    /** Cópia avulsa para hoje (sem vínculo com parcelamento, recorrência ou importação). */
    @Transactional
    public Transaction duplicate(Long userId, Long id) {
        Transaction t = require(userId, id).copy();
        t.date = calendar.today();
        t.status = Status.REALIZADO;
        t.installmentNumber = null;
        t.installmentTotal = null;
        t.installmentPlanId = null;
        t.recurrenceId = null;
        t.importBatchId = null;
        t.origin = Origin.MANUAL;
        return transactions.save(t);
    }

    // ------------------------------------------------------------------ exclusão reversível

    /** Marca como excluído (o "Desfazer" restaura); devolve os ids afetados. */
    @Transactional
    public List<Long> delete(Long userId, Long id, Scope scope) {
        Transaction t = require(userId, id);
        List<Transaction> targets = new ArrayList<>(List.of(t));
        if (scope == Scope.FOLLOWING && t.installmentPlanId != null) {
            transactions.findByPlan(userId, t.installmentPlanId).stream()
                    .filter(o -> o.installmentNumber != null && o.installmentNumber > t.installmentNumber)
                    .forEach(targets::add);
        } else if (scope == Scope.FOLLOWING && t.recurrenceId != null) {
            Recurrence r = recurrences.require(userId, t.recurrenceId);
            r.endDate = t.date.minusDays(1);
            targets.addAll(transactions.findRecurrenceFrom(userId, r.id, t.date.plusDays(1)));
        }
        Instant now = Instant.now();
        targets.forEach(x -> x.deletedAt = now);
        return targets.stream().map(x -> x.id).toList();
    }

    @Transactional
    public void restore(Long userId, List<Long> ids) {
        for (Transaction t : transactions.findByIdInAndUserId(ids, userId)) {
            if (t.deletedAt == null) continue;
            t.deletedAt = null;
            if (t.recurrenceId != null) recurrences.reopenIfEndedBefore(userId, t.recurrenceId, t.date);
        }
    }

    /** Remove de vez o que foi excluído há mais de um dia (depois disso não há mais "Desfazer"). */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeDeleted() {
        transactions.purgeDeletedBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
