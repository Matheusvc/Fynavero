package br.com.fynavero.security;

import br.com.fynavero.account.Profile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Abre a sessão da pessoa (e-mail e senha, link mágico ou Google) com proteção contra fixação de sessão. */
@Component
public class SessionAuthenticator {
    private final SecurityContextRepository contextRepo;
    private final CsrfTokenRepository csrfRepo;

    public SessionAuthenticator(SecurityContextRepository contextRepo, CsrfTokenRepository csrfRepo) {
        this.contextRepo = contextRepo;
        this.csrfRepo = csrfRepo;
    }

    public void signIn(Profile profile, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId();
        else req.getSession(true);
        AuthUser principal = new AuthUser(profile.id, profile.email, null); // sem hash de senha na sessão
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, res);
        csrfRepo.saveToken(null, req, res); // novo token CSRF depois do login
    }
}
