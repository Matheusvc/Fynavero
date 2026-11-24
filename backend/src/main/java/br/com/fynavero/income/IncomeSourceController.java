package br.com.fynavero.income;

import br.com.fynavero.income.IncomeDtos.IncomeSourceDto;
import br.com.fynavero.income.IncomeDtos.IncomeSourceReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/income-sources")
public class IncomeSourceController {
    private final IncomeSourceService incomes;

    public IncomeSourceController(IncomeSourceService incomes) {
        this.incomes = incomes;
    }

    @GetMapping
    public List<IncomeSourceDto> list(@AuthenticationPrincipal AuthUser u) {
        return incomes.list(u.id());
    }

    @PostMapping
    public IncomeSourceDto create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody IncomeSourceReq body) {
        return incomes.create(u.id(), body);
    }

    @PutMapping("/{id}")
    public IncomeSourceDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody IncomeSourceReq body) {
        return incomes.update(u.id(), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        incomes.delete(u.id(), id);
        return ResponseEntity.noContent().build();
    }
}
