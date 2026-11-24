package br.com.fynavero.ledger;

import br.com.fynavero.ledger.LedgerDtos.DeletedDto;
import br.com.fynavero.ledger.LedgerDtos.PlanResizeReq;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/installment-plans")
public class InstallmentPlanController {
    private final InstallmentService installments;
    private final TransactionService transactions;

    public InstallmentPlanController(InstallmentService installments, TransactionService transactions) {
        this.installments = installments;
        this.transactions = transactions;
    }

    /** Muda o número total de parcelas. */
    @PutMapping("/{id}")
    public List<TxDto> resize(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody PlanResizeReq body) {
        return installments.resize(u.id(), id, body.count()).stream().map(TxDto::of).toList();
    }

    /** Exclui a compra parcelada inteira; os ids devolvidos servem para o "Desfazer" (POST /api/transactions/restore). */
    @DeleteMapping("/{id}")
    public DeletedDto delete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return new DeletedDto(transactions.deletePlan(u.id(), id));
    }
}
