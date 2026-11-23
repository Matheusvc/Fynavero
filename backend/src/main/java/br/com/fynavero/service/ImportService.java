package br.com.fynavero.service;

import br.com.fynavero.api.Dto.*;
import br.com.fynavero.domain.Entities.*;
import br.com.fynavero.domain.Enums.Kind;
import br.com.fynavero.domain.Enums.Origin;
import br.com.fynavero.domain.Enums.Status;
import br.com.fynavero.domain.Repositories.ImportBatchRepo;
import br.com.fynavero.domain.Repositories.TransactionRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static br.com.fynavero.support.Support.*;

/**
 * A planilha é lida e descartada no navegador; aqui chegam só as linhas já revisadas.
 * Cada importação vira um lote que pode ser desfeito sem afetar outros lançamentos.
 */
@Service
public class ImportService {
    private final TransactionRepo txs;
    private final ImportBatchRepo batches;
    private final CategoryService categories;
    private final LedgerService ledger;

    public ImportService(TransactionRepo txs, ImportBatchRepo batches, CategoryService categories, LedgerService ledger) {
        this.txs = txs;
        this.batches = batches;
        this.categories = categories;
        this.ledger = ledger;
    }

    private static String key(LocalDate d, long cents, String desc) {
        return d + "|" + cents + "|" + (desc == null ? "" : LedgerService.fold(desc));
    }

    public DuplicateCheckDto checkDuplicates(Long userId, List<DuplicateCandidate> rows) {
        if (rows.isEmpty()) return new DuplicateCheckDto(List.of());
        LocalDate min = rows.stream().map(DuplicateCandidate::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = rows.stream().map(DuplicateCandidate::date).max(LocalDate::compareTo).orElseThrow();
        Set<String> existing = new HashSet<>();
        Set<String> existingNoDesc = new HashSet<>();
        for (Transaction t : txs.findRange(userId, min, max)) {
            existing.add(key(t.date, t.amountCents, t.description));
            existingNoDesc.add(t.date + "|" + t.amountCents);
        }
        List<Integer> dup = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DuplicateCandidate r = rows.get(i);
            boolean hit = r.description() == null || r.description().isBlank()
                    ? existingNoDesc.contains(r.date() + "|" + r.amountCents())
                    : existing.contains(key(r.date(), r.amountCents(), r.description()));
            if (hit) dup.add(i);
        }
        return new DuplicateCheckDto(dup);
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

        LocalDate today = ledger.today();
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
            t.status = r.date().isAfter(today) ? Status.PREVISTO : Status.REALIZADO;
            t.installmentNumber = r.installmentNumber();
            t.installmentTotal = r.installmentTotal();
            t.origin = Origin.IMPORTADO;
            t.importBatchId = b.id;
            t.note = trimOrNull(r.note());
            t.createdAt = Instant.now();
            list.add(t);
        }
        txs.saveAll(list);
        return dto(b, list);
    }

    public List<ImportBatchDto> list(Long userId) {
        return batches.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> dto(b, b.undoneAt == null
                        ? txs.findByBatch(userId, b.id).stream().filter(t -> t.deletedAt == null).toList()
                        : List.of()))
                .toList();
    }

    @Transactional
    public ImportBatchDto undo(Long userId, Long id) {
        ImportBatch b = batches.findByIdAndUserId(id, userId).orElseThrow(() -> notFound());
        if (b.undoneAt != null) throw badRequest("Esta importação já foi desfeita.");
        txs.deleteAll(txs.findByBatch(userId, b.id));
        b.undoneAt = Instant.now();
        return dto(b, List.of());
    }

    private static ImportBatchDto dto(ImportBatch b, List<Transaction> list) {
        long rec = 0, des = 0, inv = 0;
        for (Transaction t : list) {
            if (t.kind == Kind.RECEITA) rec += t.amountCents;
            else if (t.kind == Kind.DESPESA) des += t.amountCents;
            else inv += t.amountCents;
        }
        return new ImportBatchDto(b.id, b.fileName, b.rowCount, b.createdAt, b.undoneAt, rec, des, inv);
    }
}
