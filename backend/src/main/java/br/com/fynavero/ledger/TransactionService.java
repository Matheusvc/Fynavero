package br.com.fynavero.ledger;

import br.com.fynavero.ledger.Transaction.TransactionRepository;
import br.com.fynavero.ledger.TagColor.TagColorRepository;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.Origin;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import br.com.fynavero.ledger.Transaction.Scope;
import br.com.fynavero.ledger.Transaction.Status;
import br.com.fynavero.ledger.LedgerDtos.InstallmentConvertReq;
import br.com.fynavero.ledger.LedgerDtos.LoanRepayment;
import br.com.fynavero.ledger.LedgerDtos.TagDto;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;
import static br.com.fynavero.shared.Formats.fold;
import static br.com.fynavero.shared.Formats.paletteColor;
import static br.com.fynavero.shared.Formats.trimOrNull;

/** Lançamentos: consulta, criação (simples, recorrente, parcelada, empréstimo), edição e exclusão reversível. */
@Service
public class TransactionService {
    private static final int SEARCH_LIMIT = 500;

    private final TransactionRepository transactions;
    private final RecurrenceService recurrences;
    private final InstallmentService installments;
    private final CategoryService categories;
    private final LedgerCalendar calendar;
    private final TagColorRepository tagColors;

    public TransactionService(TransactionRepository transactions, RecurrenceService recurrences,
                              InstallmentService installments, CategoryService categories, LedgerCalendar calendar,
                              TagColorRepository tagColors) {
        this.transactions = transactions;
        this.recurrences = recurrences;
        this.installments = installments;
        this.categories = categories;
        this.calendar = calendar;
        this.tagColors = tagColors;
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

    /**
     * Busca por descrição, observação, etiqueta ou nome da categoria (sem acentos) e filtros da tela de Lançamentos.
     * "#viagem" na busca procura só a etiqueta.
     */
    public List<Transaction> filter(List<Transaction> list, String q, String tag, Long categoryId, Kind kind,
                                    PaymentMethod pm, Map<Long, Category> cats) {
        String text = q == null ? "" : q.strip();
        if (text.startsWith("#") && !text.contains(" ")) {
            tag = tag == null ? text : tag;
            text = "";
        }
        String needle = text.isEmpty() ? null : fold(text);
        String onlyTag = tag == null || tag.isBlank() ? null : tag;
        return list.stream()
                .filter(t -> onlyTag == null || Tags.has(t.tags, onlyTag))
                .filter(t -> categoryId == null || categoryId.equals(t.categoryId))
                .filter(t -> kind == null || kind == t.kind)
                .filter(t -> pm == null || pm == t.paymentMethod)
                .filter(t -> needle == null
                        || (t.description != null && fold(t.description).contains(needle))
                        || (t.note != null && fold(t.note).contains(needle))
                        || (t.tags != null && fold(t.tags).contains(needle))
                        || (cats.containsKey(t.categoryId) && fold(cats.get(t.categoryId).name).contains(needle)))
                .toList();
    }

    /** Busca em um período longo (padrão: o ano atual), com a soma por tipo do que foi encontrado. */
    @Transactional
    public SearchResult search(Long userId, String q, String tag, LocalDate from, LocalDate to, Kind kind,
                               Map<Long, Category> cats) {
        LocalDate start = from != null ? from : calendar.today().withDayOfYear(1);
        LocalDate end = to != null ? to : start.withDayOfYear(start.lengthOfYear());
        if (end.isBefore(start)) throw badRequest("O fim do período vem antes do início.");
        if (start.plusYears(5).isBefore(end)) throw badRequest("Escolha um período de até 5 anos.");
        List<Transaction> found = filter(range(userId, start, end), q, tag, null, kind, null, cats);
        long rec = 0, des = 0, inv = 0;
        for (Transaction t : found) {
            switch (t.kind) {
                case RECEITA -> rec = Math.addExact(rec, t.amountCents);
                case DESPESA -> des = Math.addExact(des, t.amountCents);
                case INVESTIMENTO -> inv = Math.addExact(inv, t.amountCents);
            }
        }
        boolean truncated = found.size() > SEARCH_LIMIT;
        return new SearchResult(truncated ? found.subList(0, SEARCH_LIMIT) : found, rec, des, inv, truncated);
    }

    public record SearchResult(List<Transaction> items, long receitas, long despesas, long investido, boolean truncated) {}

    /** Etiquetas já usadas pela pessoa, das mais usadas para as menos usadas. */
    /** Etiquetas em uso, das mais usadas para as menos, com a cor escolhida (nula = neutra). */
    public List<TagDto> tags(Long userId) {
        Map<String, Integer> count = new HashMap<>();
        for (Transaction t : transactions.findAllActive(userId)) {
            for (String tag : Tags.list(t.tags)) count.merge(tag, 1, Integer::sum);
        }
        Map<String, String> colors = new HashMap<>();
        tagColors.findByUserId(userId).forEach(c -> colors.put(c.tag, c.color));
        return count.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new TagDto(e.getKey(), e.getValue(), colors.get(e.getKey())))
                .toList();
    }

    /** Escolhe (ou tira, com cor nula) a cor de uma etiqueta. */
    @Transactional
    public TagDto setTagColor(Long userId, String rawTag, String rawColor) {
        String tag = Tags.clean(rawTag);
        if (tag == null) throw badRequest("Informe a etiqueta.");
        String color = paletteColor(rawColor);
        var current = tagColors.findByUserIdAndTag(userId, tag);
        if (color == null) current.ifPresent(tagColors::delete);
        else {
            TagColor c = current.orElseGet(TagColor::new);
            c.userId = userId;
            c.tag = tag;
            c.color = color;
            tagColors.save(c);
        }
        return tags(userId).stream().filter(t -> t.tag().equals(tag)).findFirst().orElse(new TagDto(tag, 0, color));
    }

    /** Todos os lançamentos de uma categoria, do mais novo para o mais antigo (ex.: o que já foi guardado num objetivo). */
    @Transactional(readOnly = true)
    public List<Transaction> byCategory(Long userId, Long categoryId) {
        categories.require(userId, categoryId);
        return transactions.findByCategory(userId, categoryId);
    }

    public Transaction require(Long userId, Long id) {
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound());
    }

    // ------------------------------------------------------------------ criação

    @Transactional
    public List<Transaction> create(Long userId, TxReq req) {
        String tags = Tags.toCsv(req.tags(), req.description());
        List<Transaction> out = createEntries(userId, Tags.withoutHashtags(req));
        if (tags != null) out.forEach(t -> t.tags = tags);
        return out;
    }

    private List<Transaction> createEntries(Long userId, TxReq req) {
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
        t.tags = Tags.toCsv(req.tags() != null ? req.tags() : Tags.list(t.tags), req.description());
        t.description = trimOrNull(Tags.stripHashtags(req.description()));
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
                o.tags = t.tags;
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
        if (scope == Scope.ALL && t.installmentPlanId != null) {
            transactions.findByPlan(userId, t.installmentPlanId).stream()
                    .filter(o -> !o.id.equals(t.id))
                    .forEach(targets::add);
        } else if (scope == Scope.FOLLOWING && t.installmentPlanId != null) {
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

    /** Exclui a compra parcelada inteira (botão "Excluir" em Parcelas e Dívidas); devolve os ids para o "Desfazer". */
    @Transactional
    public List<Long> deletePlan(Long userId, Long planId) {
        List<Transaction> parcels = transactions.findByPlan(userId, planId);
        if (parcels.isEmpty()) throw notFound();
        Instant now = Instant.now();
        parcels.forEach(x -> x.deletedAt = now);
        return parcels.stream().map(x -> x.id).toList();
    }

    /** Desfaz o "Marcar como pago" apertado por engano: a conta volta a ser Previsto. */
    @Transactional
    public Transaction markUnpaid(Long userId, Long id) {
        Transaction t = require(userId, id);
        t.status = Status.PREVISTO;
        return t;
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
