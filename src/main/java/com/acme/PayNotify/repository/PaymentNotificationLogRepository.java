package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.PaymentNotificationLog;
import com.acme.PayNotify.entity.EnterpriseMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentNotificationLogRepository extends JpaRepository<PaymentNotificationLog, Long> {

    Optional<PaymentNotificationLog> findByDedupeHash(String dedupeHash);

    Optional<PaymentNotificationLog> findTopByEnterpriseAndAmountAndAppNameAndStatusOrderByNotificationReceivedAtAscIdAsc(
            EnterpriseMaster enterprise,
            BigDecimal amount,
            String appName,
            String status
    );

    @Query(value = "select * from payment_notification_log where id = :id for update", nativeQuery = true)
    Optional<PaymentNotificationLog> findByIdForUpdate(@Param("id") Long id);
}
