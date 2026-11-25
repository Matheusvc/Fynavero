package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.Me;
import br.com.fynavero.account.OnboardingDtos.OnboardingReq;
import br.com.fynavero.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {
    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @PostMapping
    public Me complete(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody OnboardingReq body) {
        return Me.of(onboarding.complete(u.id(), body));
    }
}
