package br.com.fynavero.ledger;

import br.com.fynavero.calc.FinanceCalculator;
import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.domain.Kind;
import br.com.fynavero.domain.Origin;
import br.com.fynavero.domain.PaymentMethod;
import br.com.fynavero.ledger.LedgerDtos.InstallmentConvertReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static br.com.fynavero.common.Errors.badRequest;
import static br.com.fynavero.common.Errors.notFound;
import static br.com.fynavero.common.Texts.trimOrNull;

/** Compras parceladas e dívidas: cria as parcelas mês a mês, converte lançamentos e muda o número de parcelas. */
@Service
public class InstallmentService {
    private final InstallmentPlanRepository plans;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final LedgerCalendar calendar;

    public InstallmentService(InstallmentPlanRepository plans, TransactionRepository transactions,
                              CategoryService categories, LedgerCalendar calendar) {
        this.plans = plans;
        this.transactions = transactions;
        this.categories = categories;
        this.calendar = calendar;
    }

    /** Valores das parcelas: total dividido igualmente (a última absorve os centavos) ou valor fixo por parcela. */
    public static long[] parts(long amountCents, int count, boolean perInstallment) {
        try {
            return perInstallment
                    ? FinanceCalculator.fixedInstallments(amountCents, count)
                    : FinanceCalculator.splitInstallments(amountCents, count);
        } catch (ArithmeticException e) {
            throw badRequest("Valor alto demais.");
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage() + ".");
        }
    }

    /** Cria o parcelamento: 1ª parcela em {@code first}, as seguintes no mesmo dia dos meses seguintes. */
    @Transactional
    public List<Transaction> create(Long userId, Long categoryId, String description, long[] parts,
                                    LocalDate first, PaymentMethod pm, String note) {
        int count = parts.length;
        long total = 0;
        for (long v : parts) total = Math.addExact(total, v);
        InstallmentPlan p = new InstallmentPlan();
        p.userId = userId;
        p.categoryId = categoryId;
        p.description = description;
        p.totalCents = total;
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
            t.status = calendar.statusFor(t.date);
            t.installmentNumber = i + 1;
            t.installmentTotal = count;
            t.installmentPlanId = p.id;
            t.origin = Origin.MANUAL;
            t.note = i == 0 ? note : null;
            t.createdAt = Instant.now();
            list.add(t);
        }
        return transactions.saveAll(list);
    }

    /** Transforma um lançamento simples de despesa em compra parcelada a partir da mesma data. */
    @Transactional
    public List<Transaction> convert(Transaction t, Long userId, InstallmentConvertReq req) {
        if (t.kind != Kind.DESPESA) throw badRequest("Só despesas podem ser parceladas.");
        if (t.installmentPlanId != null) throw badRequest("Este lançamento já é parcelado.");
        if (t.recurrenceId != null) throw badRequest("Lançamentos que se repetem todo mês não podem ser parcelados.");
        Category c = categories.require(userId, req.categoryId());
        if (c.kind != Kind.DESPESA) throw badRequest("Escolha uma categoria de despesa.");
        List<Transaction> created = create(userId, c.id, trimOrNull(Tags.stripHashtags(req.description())),
                parts(req.amountCents(), req.count(), req.installmentValue()), req.date(), req.paymentMethod(),
                trimOrNull(req.note()));
        String tags = req.tags() != null ? Tags.toCsv(req.tags(), req.description()) : t.tags;
        created.forEach(x -> x.tags = tags);
        transactions.delete(t);
        return created;
    }

    /**
     * Muda o número total de parcelas. Parcelas novas repetem o valor da parcela comum nos meses seguintes;
     * reduzir remove as últimas, sem apagar parcelas já vencidas.
     */
    @Transactional
    public List<Transaction> resize(Long userId, Long planId, int newCount) {
        InstallmentPlan p = plans.findByIdAndUserId(planId, userId).orElseThrow(() -> notFound());
        List<Transaction> parcels = new ArrayList<>(transactions.findByPlan(userId, planId));
        if (parcels.isEmpty()) throw notFound();
        parcels.sort(Comparator.comparing(x -> x.installmentNumber));
        LocalDate today = calendar.today();
        long due = parcels.stream().filter(x -> !x.date.isAfter(today)).count();
        if (newCount < Math.max(1, due)) {
            throw badRequest("Já " + (due == 1 ? "venceu 1 parcela" : "venceram " + due + " parcelas")
                    + ". Use pelo menos " + due + ".");
        }

        if (newCount < parcels.size()) {
            List<Transaction> drop = parcels.subList(newCount, parcels.size());
            transactions.deleteAll(new ArrayList<>(drop));
            drop.clear();
        } else {
            Transaction last = parcels.getLast();
            long regular = parcels.getFirst().amountCents;
            for (int n = parcels.size() + 1; n <= newCount; n++) {
                Transaction t = last.copy();
                t.installmentNumber = n;
                t.date = p.firstDate.plusMonths(n - 1L);
                t.amountCents = regular;
                t.status = calendar.statusFor(t.date);
                t.note = null;
                parcels.add(t);
            }
        }
        for (Transaction t : parcels) t.installmentTotal = newCount;
        transactions.saveAll(parcels);
        p.installmentCount = newCount;
        p.totalCents = parcels.stream().mapToLong(x -> x.amountCents).sum();
        return parcels;
    }

    /** Aplica a alteração de uma parcela às seguintes ("Este e os próximos") e recalcula o total do plano. */
    @Transactional
    public void applyToFollowing(Long userId, Transaction edited) {
        for (Transaction o : transactions.findByPlan(userId, edited.installmentPlanId)) {
            if (o.installmentNumber != null && o.installmentNumber > edited.installmentNumber) {
                o.amountCents = edited.amountCents;
                o.categoryId = edited.categoryId;
                o.description = edited.description;
                o.paymentMethod = edited.paymentMethod;
                o.tags = edited.tags;
            }
        }
        plans.findByIdAndUserId(edited.installmentPlanId, userId).ifPresent(p -> {
            p.categoryId = edited.categoryId;
            p.description = edited.description;
            p.totalCents = transactions.findByPlan(userId, p.id).stream().mapToLong(x -> x.amountCents).sum();
        });
    }
}
