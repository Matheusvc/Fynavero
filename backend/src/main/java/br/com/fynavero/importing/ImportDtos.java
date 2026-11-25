package br.com.fynavero.importing;

import br.com.fynavero.ledger.Transaction.Kind;
import br.com.fynavero.ledger.Transaction.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static br.com.fynavero.shared.Formats.MAX_CENTS;

/** A planilha é lida no navegador; a API recebe só as linhas já revisadas e com categoria definida. */
public final class ImportDtos {
    private ImportDtos() {}

    public record ImportRow(@NotNull Kind kind, @NotNull LocalDate date, @Size(max = 120) String description,
                            @NotNull Long categoryId, @Positive @Max(MAX_CENTS) long amountCents,
                            PaymentMethod paymentMethod, @Min(1) @Max(240) Integer installmentNumber,
                            @Min(1) @Max(240) Integer installmentTotal, @Size(max = 500) String note) {}

    public record ImportReq(@NotBlank @Size(max = 200) String fileName,
                            @Valid @NotEmpty @Size(max = 10_000) List<ImportRow> rows) {}

    public record DuplicateCandidate(@NotNull LocalDate date, long amountCents, String description) {}

    public record DuplicateCheckReq(@NotNull @Size(max = 10_000) List<DuplicateCandidate> rows) {}

    /** Posições (na lista enviada) das linhas que parecem já existir. */
    public record DuplicateCheckDto(List<Integer> duplicateIndexes) {}

    public record ImportBatchDto(Long id, String fileName, int rowCount, Instant createdAt, Instant undoneAt,
                                 long receitas, long despesas, long investido) {}
}
