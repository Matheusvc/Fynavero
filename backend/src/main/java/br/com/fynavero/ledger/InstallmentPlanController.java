package br.com.fynavero.ledger;

import br.com.fynavero.ledger.LedgerDtos.PlanResizeReq;
import br.com.fynavero.ledger.LedgerDtos.TxDto;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public InstallmentPlanController(InstallmentService installments) {
        this.installments = installments;
    }

    /** Muda o número total de parcelas. */
    @PutMapping("/{id}")
    public List<TxDto> resize(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody PlanResizeReq body) {
        return installments.resize(u.id(), id, body.count()).stream().map(TxDto::of).toList();
    }
}
