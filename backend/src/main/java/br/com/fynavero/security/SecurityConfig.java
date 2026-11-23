package br.com.fynavero.security;

import br.com.fynavero.domain.Entities.Profile;
import br.com.fynavero.domain.Repositories.ProfileRepo;
import br.com.fynavero.support.Support.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Supplier;

@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(ProfileRepo profiles) {
        return email -> profiles.findByEmailIgnoreCase(email.strip())
                .map(AuthUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        var repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(c -> c.sameSite("Lax"));
        return repo;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CsrfTokenRepository csrfRepo,
                                    SecurityContextRepository contextRepo,
                                    ObjectProvider<ClientRegistrationRepository> oauthClients,
                                    ProfileRepo profiles, AppProperties props) throws Exception {
        http
            .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .securityContext(s -> s.securityContextRepository(contextRepo))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                req -> req.getRequestURI().startsWith("/api/")))
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("FYN_SESSION"))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
                    "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://accounts.google.com"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        if (oauthClients.getIfAvailable() != null) {
            log.info("Login com Google ativado.");
            http.oauth2Login(o -> o
                .successHandler(googleSuccess(profiles, contextRepo, props))
                .failureHandler((req, res, ex) -> res.sendRedirect(props.baseUrl() + "/entrar?erro=google")));
        }
        return http.build();
    }

    /** Converte o login do Google na mesma sessão usada pelo login com e-mail e senha. */
    private AuthenticationSuccessHandler googleSuccess(ProfileRepo profiles, SecurityContextRepository repo, AppProperties props) {
        return (HttpServletRequest req, HttpServletResponse res, Authentication auth) -> {
            OAuth2User g = (OAuth2User) auth.getPrincipal();
            String email = g.getAttribute("email");
            Object verified = g.getAttribute("email_verified");
            if (email == null || !Boolean.TRUE.equals(verified)) {
                res.sendRedirect(props.baseUrl() + "/entrar?erro=google");
                return;
            }
            Profile p = profiles.findByEmailIgnoreCase(email).orElseGet(() -> {
                Profile np = new Profile();
                np.email = email.toLowerCase();
                np.name = g.getAttribute("given_name");
                np.termsAcceptedAt = Instant.now();
                np.createdAt = Instant.now();
                return profiles.save(np);
            });
            signIn(AuthUser.of(p), req, res, repo);
            res.sendRedirect(props.baseUrl() + "/");
        };
    }

    public static void signIn(AuthUser user, HttpServletRequest req, HttpServletResponse res, SecurityContextRepository repo) throws IOException {
        if (req.getSession(false) != null) req.changeSessionId();
        else req.getSession(true);
        AuthUser principal = new AuthUser(user.id(), user.email(), null); // sem hash de senha na sessão
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        repo.saveContext(ctx, req, res);
    }

    /** CSRF para SPA: o token vem no cookie XSRF-TOKEN e volta no cabeçalho X-XSRF-TOKEN. */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String header = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
