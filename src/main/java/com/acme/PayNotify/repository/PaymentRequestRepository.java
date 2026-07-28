/*
 * File: PaymentRequestRepository.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Provides database access methods for PayNotify persistence.
 */
package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Optional<PaymentRequest> findByPaymentId(String paymentId);

    Optional<PaymentRequest> findTopByTransactionRefAndStatusInOrderByCreatedAtDesc(String transactionRef, List<String> statuses);

    Optional<PaymentRequest> findTopByEnterpriseAndTerminalIdAndStatusOrderByCreatedAtDesc(
            EnterpriseMaster enterprise,
            String terminalId,
            String status
    );

    List<PaymentRequest> findByTerminalIdAndStatusIn(String terminalId, List<String> statuses);

    List<PaymentRequest> findByMatchedNotificationIdAndStatus(Long matchedNotificationId, String status);

    List<PaymentRequest> findByEnterpriseAndAmountAndStatus(
            EnterpriseMaster enterprise,
            BigDecimal amount,
            String status
    );

    @Query("select p from PaymentRequest p "
            + "where p.enterprise = :enterprise "
            + "and p.status in :statuses "
            + "and (p.expiresAt is null or p.expiresAt >= :now) "
            + "order by p.createdAt desc")
    List<PaymentRequest> findActiveEnterpriseAttemptsForPhonePeFallback(
            @Param("enterprise") EnterpriseMaster enterprise,
            @Param("statuses") List<String> statuses,
            @Param("now") Timestamp now
    );

    @Query("select p from PaymentRequest p "
            + "where p.enterprise = :enterprise "
            + "and p.amount = :amount "
            + "and p.status in :statuses "
            + "and p.createdAt <= :notificationTime "
            + "and p.expiresAt >= :graceStart "
            + "order by p.createdAt desc")
    List<PaymentRequest> findActiveEnterpriseAttemptsForPhonePe(
            @Param("enterprise") EnterpriseMaster enterprise,
            @Param("amount") BigDecimal amount,
            @Param("statuses") List<String> statuses,
            @Param("notificationTime") Timestamp notificationTime,
            @Param("graceStart") Timestamp graceStart
    );

    @Query("select p from PaymentRequest p "
            + "where p.terminalId = :terminalId "
            + "and p.amount = :amount "
            + "and p.status = :status "
            + "and p.createdAt <= :notificationTime "
            + "and p.expiresAt >= :graceStart "
            + "order by p.createdAt desc")
    List<PaymentRequest> findActiveAttemptForPhonePe(
            @Param("terminalId") String terminalId,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("notificationTime") Timestamp notificationTime,
            @Param("graceStart") Timestamp graceStart
    );

    @Query(value = "select * from payment_request where id = :id for update", nativeQuery = true)
    Optional<PaymentRequest> findByIdForUpdate(@Param("id") Long id);
}
