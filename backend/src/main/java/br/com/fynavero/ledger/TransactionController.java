package br.com.fynavero.ledger;

import br.com.fynavero.category.CategoryService;
import br.com.fynavero.shared.AppProperties;
import br.com.fynavero.ledger.Kind;
import br.com.fynavero.ledger.PaymentMethod;
import br.com.fynavero.ledger.Scope;
import br.com.fynavero.ledger.LedgerDtos.DeletedDto;
import br.com.fynavero.ledger.LedgerDtos.IdsReq;
import br.com.fynavero.ledger.LedgerDtos.InstallmentConvertReq;
import br.com.fynavero.ledger.LedgerDtos.SearchDto;
import br.com.fynavero.ledger.LedgerDtos.TagDto;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.ledger.LedgerDtos.TxReq;
import br.com.fynavero.ledger.LedgerDtos.TxUpdateReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static br.com.fynavero.shared.Formats.parseMonth;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactions;
    private final CategoryService categories;
    private final AppProperties props;

    public TransactionController(TransactionService transactions, CategoryService categories, AppProperties props) {
        this.transactions = transactions;
        this.categories = categories;
        this.props = props;
    }

    @GetMapping
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
    @GetMapping("/search")
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
    @GetMapping("/by-category/{categoryId}")
    public List<TxDto> byCategory(@AuthenticationPrincipal AuthUser u, @PathVariable Long categoryId) {
        return transactions.byCategory(u.id(), categoryId).stream().map(TxDto::of).toList();
    }

    @GetMapping("/tags")
    public List<TagDto> tags(@AuthenticationPrincipal AuthUser u) {
        return transactions.tags(u.id()).stream().map(e -> new TagDto(e.getKey(), e.getValue())).toList();
    }

    @PostMapping
    public List<TxDto> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody TxReq body) {
        return transactions.create(u.id(), body).stream().map(TxDto::of).toList();
    }

    @PutMapping("/{id}")
    public TxDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                        @RequestParam(defaultValue = "ONE") Scope scope, @Valid @RequestBody TxUpdateReq body) {
        return TxDto.of(transactions.update(u.id(), id, scope, body));
    }

    @DeleteMapping("/{id}")
    public DeletedDto delete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                             @RequestParam(defaultValue = "ONE") Scope scope) {
        return new DeletedDto(transactions.delete(u.id(), id, scope));
    }

    @PostMapping("/restore")
    public ResponseEntity<Void> restore(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IdsReq body) {
        transactions.restore(u.id(), body.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public TxDto duplicate(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.duplicate(u.id(), id));
    }

    @PostMapping("/{id}/mark-paid")
    public TxDto markPaid(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.markPaid(u.id(), id));
    }

    @PostMapping("/{id}/mark-unpaid")
    public TxDto markUnpaid(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return TxDto.of(transactions.markUnpaid(u.id(), id));
    }

    @PostMapping("/{id}/installments")
    public List<TxDto> convertToInstallments(@AuthenticationPrincipal AuthUser u, @PathVariable Long id,
                                             @Valid @RequestBody InstallmentConvertReq body) {
        return transactions.convertToInstallments(u.id(), id, body).stream().map(TxDto::of).toList();
    }
}
