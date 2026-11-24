package br.com.fynavero.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByUserIdAndKindAndRef(Long userId, String kind, String ref);
}
