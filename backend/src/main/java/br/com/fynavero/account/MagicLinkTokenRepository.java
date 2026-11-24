package br.com.fynavero.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {
    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
