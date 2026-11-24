package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.EmailReq;
import br.com.fynavero.account.AccountDtos.LoginReq;
import br.com.fynavero.account.AccountDtos.Me;
import br.com.fynavero.account.AccountDtos.Providers;
import br.com.fynavero.account.AccountDtos.RegisterReq;
import br.com.fynavero.account.AccountDtos.ResetPasswordReq;
import br.com.fynavero.account.AccountDtos.TokenReq;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.security.LoginThrottle;
import br.com.fynavero.security.RateLimiter;
import br.com.fynavero.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;

import static br.com.fynavero.common.Errors.unauthorized;

/** Cadastro e login (e-mail e senha ou link mágico). A saída (/api/auth/logout) é tratada pelo Spring Security. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final MagicLinkService magicLinks;
    private final AuthenticationManager authManager;
    private final SessionAuthenticator sessions;
    private final LoginThrottle throttle;
    private final ObjectProvider<ClientRegistrationRepository> oauth;
    private final AccountEmails emails;
    private final RateLimiter limiter;

    private static final Duration HOUR = Duration.ofHours(1);
    private static final String TOO_MANY = "Muitos pedidos seguidos. Espere um pouco e tente de novo.";

    public AuthController(AccountService accounts, MagicLinkService magicLinks, AuthenticationManager authManager,
                          SessionAuthenticator sessions, LoginThrottle throttle,
                          ObjectProvider<ClientRegistrationRepository> oauth, AccountEmails emails, RateLimiter limiter) {
        this.emails = emails;
        this.limiter = limiter;
        this.accounts = accounts;
        this.magicLinks = magicLinks;
        this.authManager = authManager;
        this.sessions = sessions;
        this.throttle = throttle;
        this.oauth = oauth;
    }

    private Me signIn(Profile p, HttpServletRequest req, HttpServletResponse res) {
        sessions.signIn(p, req, res);
        return Me.of(p);
    }

    /** Garante o cookie CSRF antes da primeira alteração. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/providers")
    public Providers providers() {
        return new Providers(oauth.getIfAvailable() != null, magicLinks.enabled());
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) throw unauthorized();
        try {
            return Me.of(accounts.require(user.id()));
        } catch (ResponseStatusException e) {
            throw unauthorized(); // conta excluída com sessão antiga
        }
    }

    @PostMapping("/register")
    public Me register(@Valid @RequestBody RegisterReq body, HttpServletRequest req, HttpServletResponse res) {
        // Até 5 contas novas por hora vindas do mesmo endereço: segura robôs sem atrapalhar uma família na mesma rede.
        limiter.check("register:" + req.getRemoteAddr(), 5, HOUR, "Muitas contas criadas a partir desta rede. Tente de novo em 1 hora.");
        Profile p = accounts.register(body);
        emails.sendVerification(p);
        return signIn(p, req, res);
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginReq body, HttpServletRequest req, HttpServletResponse res) {
        String email = body.email().strip();
        if (throttle.isBlocked(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente de novo em 15 minutos.");
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
        } catch (AuthenticationException e) {
            throttle.failure(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        throttle.success(email);
        return signIn(accounts.require(((AuthUser) auth.getPrincipal()).id()), req, res);
    }

    @PostMapping("/magic-link")
    public ResponseEntity<Void> magicLink(@Valid @RequestBody EmailReq body, HttpServletRequest req) {
        limitEmails(body.email(), req);
        magicLinks.request(body.email());
        return ResponseEntity.noContent().build();
    }

    /** "Esqueci minha senha": responde igual exista ou não a conta. */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailReq body, HttpServletRequest req) {
        limitEmails(body.email(), req);
        emails.requestPasswordReset(body.email());
        return ResponseEntity.noContent().build();
    }

    /** Nova senha pelo link do e-mail; já entra na conta. */
    @PostMapping("/password/reset")
    public Me resetPassword(@Valid @RequestBody ResetPasswordReq body, HttpServletRequest req, HttpServletResponse res) {
        limiter.check("reset:" + req.getRemoteAddr(), 20, HOUR, TOO_MANY);
        Profile p = emails.resetPassword(body.token(), body.password());
        throttle.success(p.email);
        return signIn(p, req, res);
    }

    /** Confirmação do e-mail pelo link (funciona com ou sem login). */
    @PostMapping("/email/verify")
    public Me verifyEmail(@Valid @RequestBody TokenReq body, HttpServletRequest req) {
        limiter.check("verify:" + req.getRemoteAddr(), 30, HOUR, TOO_MANY);
        return Me.of(emails.verifyEmail(body.token()));
    }

    /** Reenvia o e-mail de confirmação para a pessoa logada. */
    @PostMapping("/email/resend")
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

    @PostMapping("/magic-link/consume")
    public Me consume(@Valid @RequestBody TokenReq body, HttpServletRequest req, HttpServletResponse res) {
        return signIn(magicLinks.consume(body.token()), req, res);
    }
}
