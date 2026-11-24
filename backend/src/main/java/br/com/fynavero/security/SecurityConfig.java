package br.com.fynavero.security;

import br.com.fynavero.account.ProfileRepository;
import br.com.fynavero.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Sessão em cookie httpOnly + token CSRF em toda alteração. Rotas /api exigem login (exceto /api/auth);
 * o restante serve o front-end. O login com Google só é ativado quando há credenciais configuradas.
 */
@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; "
                    + "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
                    + "form-action 'self' https://accounts.google.com";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(ProfileRepository profiles) {
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
                                    GoogleLoginSuccessHandler googleSuccess, AppProperties props) throws Exception {
        http
            .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .securityContext(s -> s.securityContextRepository(contextRepo))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/actions/**").permitAll()
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
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        if (oauthClients.getIfAvailable() != null) {
            log.info("Login com Google ativado.");
            http.oauth2Login(o -> o
                .successHandler(googleSuccess)
                .failureHandler((req, res, ex) -> res.sendRedirect(props.baseUrl() + "/entrar?erro=google")));
        }
        return http.build();
    }
}
