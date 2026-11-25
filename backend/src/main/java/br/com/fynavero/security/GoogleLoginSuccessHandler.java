package br.com.fynavero.security;

import br.com.fynavero.account.Profile;
import br.com.fynavero.account.ProfileRepository;
import br.com.fynavero.shared.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

/** Converte o login do Google na mesma sessão usada pelo login com e-mail e senha. */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final ProfileRepository profiles;
    private final SessionAuthenticator sessions;
    private final AppProperties props;

    public GoogleLoginSuccessHandler(ProfileRepository profiles, SessionAuthenticator sessions, AppProperties props) {
        this.profiles = profiles;
        this.sessions = sessions;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws IOException {
        OAuth2User google = (OAuth2User) auth.getPrincipal();
        String email = google.getAttribute("email");
        if (email == null || !Boolean.TRUE.equals(google.getAttribute("email_verified"))) {
            res.sendRedirect(props.baseUrl() + "/entrar?erro=google");
            return;
        }
        Profile profile = profiles.findByEmailIgnoreCase(email).orElseGet(() -> {
            Profile p = new Profile();
            p.email = email.toLowerCase(Locale.ROOT);
            p.name = google.getAttribute("given_name");
            p.termsAcceptedAt = Instant.now(); // aceite exibido junto ao botão "Continuar com Google"
            p.emailVerifiedAt = Instant.now(); // o Google já confirmou o e-mail
            p.createdAt = Instant.now();
            return profiles.save(p);
        });
        if (profile.emailVerifiedAt == null) {
            profile.emailVerifiedAt = Instant.now();
            profiles.save(profile);
        }
        sessions.signIn(profile, req, res);
        res.sendRedirect(props.baseUrl() + "/");
    }
}
