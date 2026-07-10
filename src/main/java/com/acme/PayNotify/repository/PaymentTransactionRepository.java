package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByPaymentId(String paymentId);

    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    Optional<PaymentTransaction> findByUtr(String utr);

    List<PaymentTransaction> findByStatusAndCreatedAtAfter(String status, LocalDateTime createdAt);

    List<PaymentTransaction> findByStatusAndAmountAndCreatedAtAfter(String status, BigDecimal amount, LocalDateTime createdAt);

    Optional<PaymentTransaction> findTopByStatusOrderByCreatedAtDesc(String status);

    Optional<PaymentTransaction> findTopByTransactionRefAndStatusOrderByCreatedAtDesc(String transactionRef, String status);

    Optional<PaymentTransaction> findTopByNoteContainingIgnoreCaseAndStatusOrderByCreatedAtDesc(String notePart, String status);
}
