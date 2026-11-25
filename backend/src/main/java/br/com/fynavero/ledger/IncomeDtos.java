package br.com.fynavero.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import static br.com.fynavero.shared.Formats.MAX_CENTS;

public final class IncomeDtos {
    private IncomeDtos() {}

    /** day: dia do mês em que a renda entra (vem da recorrência); nulo quando não há valor cadastrado. */
    public record IncomeSourceDto(Long id, String name, long amountCents, boolean fixed, Long categoryId, Integer day) {}

    public record IncomeSourceReq(@NotBlank @Size(max = 60) String name,
                                  @PositiveOrZero @Max(MAX_CENTS) long amountCents,
                                  boolean fixed,
                                  @Min(1) @Max(31) Integer day) {}
}
