package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,Long> {
    Optional<PaymentTransaction> findByPaymentId(String paymentId);
}
