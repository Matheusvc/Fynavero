package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.ContributionDto;
import br.com.fynavero.account.AccountDtos.DeleteAccountReq;
import br.com.fynavero.account.AccountDtos.EmailReq;
import br.com.fynavero.account.AccountDtos.LoginReq;
import br.com.fynavero.account.AccountDtos.Me;
import br.com.fynavero.account.AccountDtos.OnboardingReq;
import br.com.fynavero.account.AccountDtos.PasswordReq;
import br.com.fynavero.account.AccountDtos.ProfileReq;
import br.com.fynavero.account.AccountDtos.Providers;
import br.com.fynavero.account.AccountDtos.RegisterReq;
import br.com.fynavero.account.AccountDtos.ResetPasswordReq;
import br.com.fynavero.account.AccountDtos.TokenReq;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.security.RateLimiter;
import br.com.fynavero.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static br.com.fynavero.shared.Errors.unauthorized;

/** API da conta: cadastro e login, perfil, LGPD e primeiro acesso. A saída (/api/auth/logout) é tratada pelo Spring Security. */
@RestController
public class AccountController {
    private static final Duration HOUR = Duration.ofHours(1);
    private static final String TOO_MANY = "Muitos pedidos seguidos. Espere um pouco e tente de novo.";

    private final AccountService accounts;
    private final AuthenticationManager authManager;
    private final SessionAuthenticator sessions;
    private final ObjectProvider<ClientRegistrationRepository> oauth;
    private final AccountLinks emails;
    private final RateLimiter limiter;
    private final AccountService privacy;
    private final OnboardingService onboarding;

    public AccountController(AccountService accounts, AuthenticationManager authManager, SessionAuthenticator sessions, ObjectProvider<ClientRegistrationRepository> oauth, AccountLinks emails, RateLimiter limiter, AccountService privacy, OnboardingService onboarding) {
        this.accounts = accounts;
        this.authManager = authManager;
        this.sessions = sessions;
        this.oauth = oauth;
        this.emails = emails;
        this.limiter = limiter;
        this.privacy = privacy;
        this.onboarding = onboarding;
    }

    // ---------------------------------------------------------------- /api/auth
    /** Cadastro e login (e-mail e senha ou link mágico). A saída (/api/auth/logout) é tratada pelo Spring Security. */
    private Me signIn(Profile p, HttpServletRequest req, HttpServletResponse res) {
        sessions.signIn(p, req, res);
        return Me.of(p);
    }

    /** Garante o cookie CSRF antes da primeira alteração. */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/providers")
    public Providers providers() {
        return new Providers(oauth.getIfAvailable() != null, true);
    }

    @GetMapping("/api/auth/me")
    public Me me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw unauthorized();
        try {
            return Me.of(accounts.require(user.id()));
        } catch (ResponseStatusException e) {
            throw unauthorized(); // conta excluída com sessão antiga
        }
    }

    @PostMapping("/api/auth/register")
    public Me register(@Valid @RequestBody RegisterReq body, HttpServletRequest req, HttpServletResponse res) {
        // Até 5 contas novas por hora vindas do mesmo endereço: segura robôs sem atrapalhar uma família na mesma rede.
        limiter.check("register:" + req.getRemoteAddr(), 5, HOUR, "Muitas contas criadas a partir desta rede. Tente de novo em 1 hora.");
        Profile p = accounts.register(body);
        emails.sendVerification(p);
        return signIn(p, req, res);
    }

    @PostMapping("/api/auth/login")
    public Me login(@Valid @RequestBody LoginReq body, HttpServletRequest req, HttpServletResponse res) {
        String email = body.email().strip();
        String failKey = "login-fail:" + email.toLowerCase(Locale.ROOT);
        if (limiter.isLimited(failKey, 5, Duration.ofMinutes(15))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente de novo em 15 minutos.");
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException e) {
            limiter.hit(failKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        limiter.reset(failKey);
        return signIn(accounts.require(((AuthUser) auth.getPrincipal()).id()), req, res);
    }

    @PostMapping("/api/auth/magic-link")
    public ResponseEntity<Void> magicLink(@Valid @RequestBody EmailReq body, HttpServletRequest req) {
        limitEmails(body.email(), req);
        emails.requestMagicLink(body.email());
        return ResponseEntity.noContent().build();
    }

    /** "Esqueci minha senha": responde igual exista ou não a conta. */
    @PostMapping("/api/auth/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailReq body, HttpServletRequest req) {
        limitEmails(body.email(), req);
        emails.requestPasswordReset(body.email());
        return ResponseEntity.noContent().build();
    }

    /** Nova senha pelo link do e-mail; já entra na conta. */
    @PostMapping("/api/auth/password/reset")
    public Me resetPassword(@Valid @RequestBody ResetPasswordReq body, HttpServletRequest req, HttpServletResponse res) {
        limiter.check("reset:" + req.getRemoteAddr(), 20, HOUR, TOO_MANY);
        Profile p = emails.resetPassword(body.token(), body.password());
        limiter.reset("login-fail:" + p.email.toLowerCase(Locale.ROOT));
        return signIn(p, req, res);
    }

    /** Confirmação do e-mail pelo link (funciona com ou sem login). */
    @PostMapping("/api/auth/email/verify")
    public Me verifyEmail(@Valid @RequestBody TokenReq body, HttpServletRequest req) {
        limiter.check("verify:" + req.getRemoteAddr(), 30, HOUR, TOO_MANY);
        return Me.of(emails.verifyEmail(body.token()));
    }

    /** Reenvia o e-mail de confirmação para a pessoa logada. */
    @PostMapping("/api/auth/email/resend")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw unauthorized();
        limiter.check("resend:" + user.id(), 3, HOUR, "Já enviamos 3 e-mails de confirmação na última hora. Confira também a caixa de spam.");
        emails.sendVerification(accounts.require(user.id()));
        return ResponseEntity.noContent().build();
    }

    /** E-mails disparados por pedido público: até 5 por hora para o mesmo endereço e 20 por hora da mesma rede. */
    private void limitEmails(String email, HttpServletRequest req) {
        limiter.check("mail-ip:" + req.getRemoteAddr(), 20, HOUR, TOO_MANY);
        limiter.check("mail-to:" + email.strip().toLowerCase(Locale.ROOT), 5, HOUR, TOO_MANY);
    }

    @PostMapping("/api/auth/magic-link/consume")
    public Me consume(@Valid @RequestBody TokenReq body, HttpServletRequest req, HttpServletResponse res) {
        return signIn(emails.consumeMagicLink(body.token()), req, res);
    }

    // ---------------------------------------------------------------- /api/profile
    @PutMapping("/api/profile")
    public Me update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ProfileReq body) {
        return Me.of(accounts.updateProfile(u.id(), body));
    }

    @PutMapping("/api/profile/contribution")
    public Me contribution(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ContributionDto body) {
        return Me.of(accounts.updateContribution(u.id(), body));
    }

    @PutMapping("/api/profile/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody PasswordReq body) {
        accounts.changePassword(u.id(), body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/profile/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal AuthUser u) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("fynavero-meus-dados.json").build().toString())
                .body(privacy.exportData(u.id()));
    }

    @DeleteMapping("/api/profile")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DeleteAccountReq body,
                                       HttpServletRequest req) {
        privacy.deleteAccount(u.id(), body);
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- /api/onboarding
    @PostMapping("/api/onboarding")
    public Me complete(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody OnboardingReq body) {
        return Me.of(onboarding.complete(u.id(), body));
    }
}
