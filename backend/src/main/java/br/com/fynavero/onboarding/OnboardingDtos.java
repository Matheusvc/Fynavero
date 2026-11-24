package br.com.fynavero.onboarding;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.domain.Objective;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

import static br.com.fynavero.common.ApiLimits.MAX_CENTS;

/** As 5 telas do onboarding, enviadas juntas ao final. */
public final class OnboardingDtos {
    private OnboardingDtos() {}

    public record IncomeInput(@NotBlank @Size(max = 60) String name,
                              @PositiveOrZero @Max(MAX_CENTS) long amountCents,
                              boolean fixed,
                              @Min(1) @Max(31) Integer day) {}

    public record CustomCategoryInput(@NotBlank @Size(max = 60) String name,
                                      @NotBlank @Size(max = 40) String icon,
                                      @NotBlank @Size(max = 30) String group) {}

    /** categoryKey: "catalog:{id}" ou "custom:{nome}", referência às categorias escolhidas na tela 3. */
    public record FixedBillInput(@NotBlank @Size(max = 60) String name,
                                 @Positive @Max(MAX_CENTS) long amountCents,
                                 @Min(1) @Max(31) int day,
                                 @NotBlank String categoryKey) {}

    public record OnboardingReq(@Size(max = 100) String name,
                                @Size(max = 4) List<Objective> objectives,
                                @Valid @Size(max = 20) List<IncomeInput> incomes,
                                @Size(max = 60) List<Long> catalogIds,
                                @Valid @Size(max = 30) List<CustomCategoryInput> customCategories,
                                @Valid @Size(max = 30) List<FixedBillInput> fixedBills,
                                @Valid ContributionDto contribution) {}
}
