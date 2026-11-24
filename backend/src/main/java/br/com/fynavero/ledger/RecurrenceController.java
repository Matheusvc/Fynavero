package br.com.fynavero.ledger;

import br.com.fynavero.ledger.LedgerDtos.RecurrenceDto;
import br.com.fynavero.ledger.LedgerDtos.RecurrenceReq;
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

/** Contas fixas e recorrências (Configurações). */
@RestController
@RequestMapping("/api/recurrences")
public class RecurrenceController {
    private final RecurrenceService recurrences;

    public RecurrenceController(RecurrenceService recurrences) {
        this.recurrences = recurrences;
    }

    @GetMapping
    public List<RecurrenceDto> list(@AuthenticationPrincipal AuthUser u) {
        return recurrences.active(u.id()).stream().map(RecurrenceDto::of).toList();
    }

    @PostMapping
    public RecurrenceDto create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody RecurrenceReq body) {
        return RecurrenceDto.of(recurrences.createFixedBill(u.id(), body));
    }

    @PutMapping("/{id}")
    public RecurrenceDto update(@AuthenticationPrincipal AuthUser u, @PathVariable Long id, @Valid @RequestBody RecurrenceReq body) {
        return RecurrenceDto.of(recurrences.update(u.id(), id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        recurrences.end(u.id(), id);
        return ResponseEntity.noContent().build();
    }
}
