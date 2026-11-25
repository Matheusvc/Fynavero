package br.com.fynavero.importing;

import br.com.fynavero.importing.ImportBatch.ImportBatchRepository;

import br.com.fynavero.category.Category;
import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.Origin;
import br.com.fynavero.importing.ImportDtos.DuplicateCandidate;
import br.com.fynavero.importing.ImportDtos.DuplicateCheckDto;
import br.com.fynavero.importing.ImportDtos.ImportBatchDto;
import br.com.fynavero.importing.ImportDtos.ImportReq;
import br.com.fynavero.importing.ImportDtos.ImportRow;
import br.com.fynavero.ledger.LedgerCalendar;
import br.com.fynavero.ledger.Transaction;
import br.com.fynavero.ledger.Transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.fynavero.shared.Errors.badRequest;
import static br.com.fynavero.shared.Errors.notFound;
import static br.com.fynavero.shared.Formats.fold;
import static br.com.fynavero.shared.Formats.trimOrNull;

/**
 * Importação em lote. Cada importação vira um {@link ImportBatch} que pode ser desfeito sem afetar outros lançamentos.
 */
@Service
public class ImportService {
    private final TransactionRepository transactions;
    private final ImportBatchRepository batches;
    private final CategoryService categories;
    private final LedgerCalendar calendar;

    public ImportService(TransactionRepository transactions, ImportBatchRepository batches,
                         CategoryService categories, LedgerCalendar calendar) {
        this.transactions = transactions;
        this.batches = batches;
        this.categories = categories;
        this.calendar = calendar;
    }

    /** Possível duplicado: mesma data, valor e descrição (ou só data e valor, quando a linha não tem descrição). */
    public DuplicateCheckDto checkDuplicates(Long userId, List<DuplicateCandidate> rows) {
        if (rows.isEmpty()) return new DuplicateCheckDto(List.of());
        LocalDate min = rows.stream().map(DuplicateCandidate::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = rows.stream().map(DuplicateCandidate::date).max(LocalDate::compareTo).orElseThrow();
        Set<String> withDescription = new HashSet<>();
        Set<String> withoutDescription = new HashSet<>();
        for (Transaction t : transactions.findRange(userId, min, max)) {
            withDescription.add(key(t.date, t.amountCents, t.description));
            withoutDescription.add(t.date + "|" + t.amountCents);
        }
        List<Integer> dup = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DuplicateCandidate r = rows.get(i);
            boolean hit = r.description() == null || r.description().isBlank()
                    ? withoutDescription.contains(r.date() + "|" + r.amountCents())
                    : withDescription.contains(key(r.date(), r.amountCents(), r.description()));
            if (hit) dup.add(i);
        }
        return new DuplicateCheckDto(dup);
    }

    private static String key(LocalDate d, long cents, String desc) {
        return d + "|" + cents + "|" + (desc == null ? "" : fold(desc));
    }

    @Transactional
    public ImportBatchDto importRows(Long userId, ImportReq req) {
        Map<Long, Category> cats = categories.map(userId);
        ImportBatch b = new ImportBatch();
        b.userId = userId;
        b.fileName = req.fileName().strip();
        b.rowCount = req.rows().size();
        b.createdAt = Instant.now();
        batches.save(b);

        List<Transaction> list = new ArrayList<>(req.rows().size());
        int line = 0;
        for (ImportRow r : req.rows()) {
            line++;
            Category c = cats.get(r.categoryId());
            if (c == null) throw badRequest("Linha " + line + ": categoria não encontrada.");
            if (c.kind != r.kind()) throw badRequest("Linha " + line + ": a categoria \"" + c.name + "\" não é do tipo do lançamento.");
            Transaction t = new Transaction();
            t.userId = userId;
            t.kind = r.kind();
            t.date = r.date();
            t.description = trimOrNull(r.description());
            t.categoryId = c.id;
            t.amountCents = r.amountCents();
            t.paymentMethod = r.paymentMethod();
            t.status = calendar.statusFor(r.date());
            t.installmentNumber = r.installmentNumber();
            t.installmentTotal = r.installmentTotal();
            t.origin = Origin.IMPORTADO;
            t.importBatchId = b.id;
            t.note = trimOrNull(r.note());
            t.createdAt = Instant.now();
            list.add(t);
        }
        transactions.saveAll(list);
        return dto(b, list);
    }

    public List<ImportBatchDto> list(Long userId) {
        return batches.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> dto(b, b.undoneAt != null ? List.of()
                        : transactions.findByBatch(userId, b.id).stream().filter(t -> t.deletedAt == null).toList()))
                .toList();
    }

    @Transactional
    public ImportBatchDto undo(Long userId, Long id) {
        ImportBatch b = batches.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        if (b.undoneAt != null) throw badRequest("Esta importação já foi desfeita.");
        transactions.deleteAll(transactions.findByBatch(userId, b.id));
        b.undoneAt = Instant.now();
        return dto(b, List.of());
    }

    private static ImportBatchDto dto(ImportBatch b, List<Transaction> list) {
        long receitas = 0, despesas = 0, investido = 0;
        for (Transaction t : list) {
            if (t.kind == Kind.RECEITA) receitas += t.amountCents;
            else if (t.kind == Kind.DESPESA) despesas += t.amountCents;
            else investido += t.amountCents;
        }
        return new ImportBatchDto(b.id, b.fileName, b.rowCount, b.createdAt, b.undoneAt, receitas, despesas, investido);
    }
}
