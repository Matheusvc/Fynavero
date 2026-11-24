package br.com.fynavero.account;

import br.com.fynavero.domain.Objective;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

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
}
