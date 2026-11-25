package br.com.fynavero.ledger;

import br.com.fynavero.category.CategoryService;
import br.com.fynavero.ledger.LedgerDtos.DeletedDto;
import br.com.fynavero.ledger.LedgerDtos.IdsReq;
import br.com.fynavero.ledger.LedgerDtos.IncomeSourceDto;
import br.com.fynavero.ledger.LedgerDtos.IncomeSourceReq;
import br.com.fynavero.ledger.LedgerDtos.InstallmentConvertReq;
import br.com.fynavero.ledger.LedgerDtos.PlanResizeReq;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceDto;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceReq;
import br.com.fynavero.ledger.LedgerDtos.SearchDto;
import br.com.fynavero.ledger.LedgerDtos.TagColorReq;
import br.com.fynavero.ledger.LedgerDtos.TagDto;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.ledger.LedgerDtos.TxReq;
import br.com.fynavero.ledger.LedgerDtos.TxUpdateReq;
import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import br.com.fynavero.ledger.Transaction.Scope;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.shared.AppProperties;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static br.com.fynavero.shared.Formats.parseMonth;

/** API do núcleo financeiro: lançamentos, recorrências, parcelamentos e fontes de renda. */
@RestController
public class LedgerController {
    private final TransactionService transactions;
    private final CategoryService categories;
    private final AppProperties props;
    private final RecurrenceService recurrences;
    private final InstallmentService installments;

    public LedgerController(TransactionService transactions, CategoryService categories, AppProperties props, RecurrenceService recurrences, InstallmentService installments) {
        this.transactions = transactions;
        this.categories = categories;
        this.props = props;
        this.recurrences = recurrences;
        this.installments = installments;
    }

    // ---------------------------------------------------------------- /api/transactions
    @GetMapping("/api/transactions")
    public List<TxDto> list(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String month,
                            @RequestParam(required = false) String q, @RequestParam(required = false) String tag,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) Kind kind,
                            @RequestParam(required = false) PaymentMethod paymentMethod) {
        var monthTxs = transactions.month(u.id(), parseMonth(month, props.zoneId()));
        return transactions.filter(monthTxs, q, tag, categoryId, kind, paymentMethod, categories.map(u.id()))
                .stream().map(TxDto::of).toList();
    }

    /** Busca no ano todo (ou no período informado): "farmácia", "#viagem"… */
    @GetMapping("/api/transactions/search")
    public SearchDto search(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String q,
                            @RequestParam(required = false) String tag,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) Kind kind) {
        var r = transactions.search(u.id(), q, tag, from, to, kind, categories.map(u.id()));
        return new SearchDto(r.items().stream().map(TxDto::of).toList(), r.receitas(), r.despesas(), r.investido(),
                r.truncated());
    }

    /** Histórico completo de uma categoria: usado para ver e corrigir o que foi guardado em cada investimento. */
    @GetMapping("/api/transactions/by-category/{categoryId}")
    public List<TxDto> byCategory(@AuthenticationPrincipal AuthUser u, @PathVariable Long categoryId) {
        return transactions.byCategory(u.id(), categoryId).stream().map(TxDto::of).toList();
    }

    @GetMapping("/api/transactions/tags")
    public List<TagDto> tags(@AuthenticationPrincipal AuthUser u) {
        return transactions.tags(u.id());
    }

    @PutMapping("/api/transactions/tags/color")
    public TagDto tagColor(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody TagColorReq body) {
        return transactions.setTagColor(u.id(), body.tag(), body.color());
    }

    @PostMapping("/api/transactions")
    public List<TxDto> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody TxReq body) {
        return transactions.create(u.id(), body).stream().map(TxDto::of).toList();
    }

    @PutMapping("/api/transactions/{id}")
    public TxDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                        @RequestParam(defaultValue = "ONE") Scope scope, @Valid @RequestBody TxUpdateReq body) {
        return TxDto.of(transactions.update(u.id(), id, scope, body));
    }

    @DeleteMapping("/api/transactions/{id}")
    public DeletedDto delete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                             @RequestParam(defaultValue = "ONE") Scope scope) {
        return new DeletedDto(transactions.delete(u.id(), id, scope));
    }

    @PostMapping("/api/transactions/restore")
    public ResponseEntity<Void> restore(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IdsReq body) {
        transactions.restore(u.id(), body.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/transactions/{id}/duplicate")
    public TxDto duplicate(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.duplicate(u.id(), id));
    }

    @PostMapping("/api/transactions/{id}/mark-paid")
    public TxDto markPaid(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.markPaid(u.id(), id));
    }

    @PostMapping("/api/transactions/{id}/mark-unpaid")
    public TxDto markUnpaid(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.markUnpaid(u.id(), id));
    }

    @PostMapping("/api/transactions/{id}/installments")
    public List<TxDto> convertToInstallments(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                             @Valid @RequestBody InstallmentConvertReq body) {
        return transactions.convertToInstallments(u.id(), id, body).stream().map(TxDto::of).toList();
    }

    // ---------------------------------------------------------------- /api/recurrences
    /** Contas fixas e recorrências (Configurações). */
    @GetMapping("/api/recurrences")
    public List<RecurrenceDto> listRecurrences(@AuthenticationPrincipal AuthUser u) {
        return recurrences.active(u.id()).stream().map(RecurrenceDto::of).toList();
    }

    @PostMapping("/api/recurrences")
    public RecurrenceDto createRecurrence(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody RecurrenceReq body) {
        return RecurrenceDto.of(recurrences.createFixedBill(u.id(), body));
    }

    @PutMapping("/api/recurrences/{id}")
    public RecurrenceDto updateRecurrence(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody RecurrenceReq body) {
        return RecurrenceDto.of(recurrences.update(u.id(), id, body));
    }

    @DeleteMapping("/api/recurrences/{id}")
    public ResponseEntity<Void> endRecurrence(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        recurrences.end(u.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- /api/installment-plans
    /** Muda o número total de parcelas. */
    @PutMapping("/api/installment-plans/{id}")
    public List<TxDto> resizePlan(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody PlanResizeReq body) {
        return installments.resize(u.id(), id, body.count()).stream().map(TxDto::of).toList();
    }

    /** Exclui a compra parcelada inteira; os ids devolvidos servem para o "Desfazer" (POST /api/transactions/restore). */
    @DeleteMapping("/api/installment-plans/{id}")
    public DeletedDto deletePlan(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return new DeletedDto(transactions.deletePlan(u.id(), id));
    }

    // ---------------------------------------------------------------- /api/income-sources
    @GetMapping("/api/income-sources")
    public List<IncomeSourceDto> listIncomeSources(@AuthenticationPrincipal AuthUser u) {
        return recurrences.list(u.id());
    }

    @PostMapping("/api/income-sources")
    public IncomeSourceDto createIncomeSource(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IncomeSourceReq body) {
        return recurrences.create(u.id(), body);
    }

    @PutMapping("/api/income-sources/{id}")
    public IncomeSourceDto updateIncomeSource(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody IncomeSourceReq body) {
        return recurrences.update(u.id(), id, body);
    }

    @DeleteMapping("/api/income-sources/{id}")
    public ResponseEntity<Void> deleteIncomeSource(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        recurrences.delete(u.id(), id);
        return ResponseEntity.noContent().build();
    }
}
