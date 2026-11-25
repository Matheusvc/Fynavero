package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);

    /** Invalida os links ainda abertos daquele tipo (ao pedir um novo ou ao usar um). */
    @Modifying
    @Query("update AccountToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidate(@Param("userId") Long userId, @Param("purpose") Purpose purpose, @Param("now") Instant now);

    void deleteByUserId(Long userId);
}
