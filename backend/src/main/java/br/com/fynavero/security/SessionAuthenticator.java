package br.com.fynavero.security;

import br.com.fynavero.account.Profile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/** Abre a sessão da pessoa (e-mail e senha, link mágico ou Google) com proteção contra fixação de sessão. */
@Component
public class SessionAuthenticator {
    private final SecurityContextRepository contextRepo;
    private final CsrfTokenRepository csrfRepo;
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> stored;

    public SessionAuthenticator(SecurityContextRepository contextRepo, CsrfTokenRepository csrfRepo,
                                ObjectProvider<FindByIndexNameSessionRepository<?>> stored) {
        this.contextRepo = contextRepo;
        this.csrfRepo = csrfRepo;
        this.stored = stored;
    }

    /**
     * Depois de trocar a senha: encerra as sessões em outros aparelhos (quando ficam no banco) e troca o id da
     * sessão atual — quem tinha roubado o cookie antigo perde o acesso.
     */
    public void endOtherSessions(String email, HttpServletRequest req) {
        String current = req.getSession(false) == null ? null : req.changeSessionId();
        FindByIndexNameSessionRepository<?> repo = stored.getIfAvailable();
        if (repo == null) return;
        repo.findByPrincipalName(email).keySet().stream().filter(id -> !id.equals(current)).forEach(repo::deleteById);
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
