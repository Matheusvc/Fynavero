package br.com.fynavero.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActionTokenRepository extends JpaRepository<ActionToken, Long> {
    Optional<ActionToken> findByTokenHash(String tokenHash);
}
