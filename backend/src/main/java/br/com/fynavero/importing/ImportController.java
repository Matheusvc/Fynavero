package br.com.fynavero.importing;

import br.com.fynavero.importing.ImportDtos.DuplicateCheckDto;
import br.com.fynavero.importing.ImportDtos.DuplicateCheckReq;
import br.com.fynavero.importing.ImportDtos.ImportBatchDto;
import br.com.fynavero.importing.ImportDtos.ImportReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/check")
    public DuplicateCheckDto check(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DuplicateCheckReq body) {
        return imports.checkDuplicates(u.id(), body.rows());
    }

    @PostMapping
    public ImportBatchDto importRows(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ImportReq body) {
        return imports.importRows(u.id(), body);
    }

    @GetMapping
    public List<ImportBatchDto> list(@AuthenticationPrincipal AuthUser u) {
        return imports.list(u.id());
    }

    /** Desfaz a importação: remove só os lançamentos deste lote. */
    @DeleteMapping("/{id}")
    public ImportBatchDto undo(@AuthenticationPrincipal AuthUser u, @PathVariable Long id) {
        return imports.undo(u.id(), id);
    }
}
