package br.com.fynavero.account;

import br.com.fynavero.account.AccountDtos.EmailReq;
import br.com.fynavero.account.AccountDtos.LoginReq;
import br.com.fynavero.account.AccountDtos.Me;
import br.com.fynavero.account.AccountDtos.Providers;
import br.com.fynavero.account.AccountDtos.RegisterReq;
import br.com.fynavero.account.AccountDtos.TokenReq;
import br.com.fynavero.security.AuthUser;
import br.com.fynavero.security.LoginThrottle;
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

    public AuthController(AccountService accounts, MagicLinkService magicLinks, AuthenticationManager authManager,
                          SessionAuthenticator sessions, LoginThrottle throttle,
                          ObjectProvider<ClientRegistrationRepository> oauth) {
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
        return signIn(accounts.register(body), req, res);
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
    public ResponseEntity<Void> magicLink(@Valid @RequestBody EmailReq body) {
        magicLinks.request(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/magic-link/consume")
    public Me consume(@Valid @RequestBody TokenReq body, HttpServletRequest req, HttpServletResponse res) {
        return signIn(magicLinks.consume(body.token()), req, res);
    }
}
