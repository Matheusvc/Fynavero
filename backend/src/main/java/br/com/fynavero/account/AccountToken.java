package br.com.fynavero.account;

import br.com.fynavero.account.AccountToken.Purpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Link de uso único enviado por e-mail. No banco fica só o hash SHA-256 do token. Ver {@link AccountLinks}. */
@Entity
@Table(name = "account_tokens")
public class AccountToken {
    public enum Purpose { MAGIC_LINK, RESET_PASSWORD, VERIFY_EMAIL, PAY_BILL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id") public Long userId;
    @Enumerated(EnumType.STRING) public Purpose purpose;
    /** Objeto a que o link se refere (no PAY_BILL, o id do lançamento). */
    @Column(name = "ref_id") public Long refId;
    @Column(name = "token_hash") public String tokenHash;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "used_at") public Instant usedAt;
    @Column(name = "created_at") public Instant createdAt;

    public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
        Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);
    
        /** Invalida os links ainda abertos daquele tipo (ao pedir um novo ou ao usar um). */
        @Modifying
        @Query("update AccountToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
        int invalidate(@Param("userId") Long userId, @Param("purpose") Purpose purpose, @Param("now") Instant now);
    
        void deleteByUserId(Long userId);
    }
}
