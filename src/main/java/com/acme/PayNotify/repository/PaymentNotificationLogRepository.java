package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.PaymentNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentNotificationLogRepository extends JpaRepository<PaymentNotificationLog, Long> {
}