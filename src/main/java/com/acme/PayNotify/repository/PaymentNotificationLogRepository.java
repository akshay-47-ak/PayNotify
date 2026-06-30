package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.PaymentNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentNotificationLogRepository extends JpaRepository<PaymentNotificationLog, Long> {

    Optional<PaymentNotificationLog> findByDedupeHash(String dedupeHash);

    @Query(value = "select * from payment_notification_log where id = :id for update", nativeQuery = true)
    Optional<PaymentNotificationLog> findByIdForUpdate(@Param("id") Long id);
}
