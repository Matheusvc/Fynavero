package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.Profile.Objective;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import static br.com.fynavero.shared.Formats.MAX_CENTS;

/** Contratos da API de conta, login e perfil. */
public final class AccountDtos {
    private AccountDtos() {}

    public record RegisterReq(@NotBlank @Size(max = 100) String name,
                              @NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 8, max = 72) String password,
                              @AssertTrue(message = "Aceite os termos para continuar.") boolean acceptTerms) {}

    public record LoginReq(@NotBlank String email, @NotBlank String password) {}

    public record EmailReq(@NotBlank @Email String email) {}

    public record TokenReq(@NotBlank @Size(max = 200) String token) {}

    /** Formas de login disponíveis neste servidor. */
    public record Providers(boolean google, boolean magicLink) {}

    /** Contribuição automática (dízimo ou doação); bps em pontos-base: 1000 = 10%. */
    public record ContributionDto(boolean enabled, @Min(1) @Max(10000) int bps, Long categoryId) {}

    public record Me(Long id, String name, String email, List<Objective> objectives, boolean onboardingDone,
                     ContributionDto contribution, boolean hasPassword, boolean emailVerified) {
        public static Me of(Profile p) {
            return new Me(p.id, p.name, p.email, p.objectives(), p.onboardingDone,
                    new ContributionDto(p.contributionEnabled, p.contributionBps, p.contributionCategoryId),
                    p.passwordHash != null, p.emailVerifiedAt != null);
        }
    }

    public record ProfileReq(@NotBlank @Size(max = 100) String name, @Size(max = 4) List<Objective> objectives) {}

    /** Nova senha a partir do link de "Esqueci minha senha". */
    public record ResetPasswordReq(@NotBlank @Size(max = 200) String token, @NotBlank @Size(min = 8, max = 72) String password) {}

    public record PasswordReq(String current, @NotBlank @Size(min = 8, max = 72) String next) {}

    public record DeleteAccountReq(@NotBlank String confirm, String password) {}

    // ---------------------------------------------------------------- Onboarding
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
