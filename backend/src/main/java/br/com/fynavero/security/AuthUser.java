package br.com.fynavero.security;

import br.com.fynavero.account.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Pessoa autenticada guardada na sessão. Só o id é usado para acessar dados. */
public record AuthUser(Long id, String email, String passwordHash) implements UserDetails {

    public static AuthUser of(Profile p) {
        return new AuthUser(p.id, p.email, p.passwordHash);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_USER")); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }

    @Override public String toString() { return "AuthUser[id=" + id + "]"; }
}
