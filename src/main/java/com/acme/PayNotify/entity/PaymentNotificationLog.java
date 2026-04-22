package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "payment_notification_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private EnterpriseMaster enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_device_id")
    private UserDevice userDevice;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "parsed_transaction_ref", length = 100)
    private String parsedTransactionRef;

    @Column(name = "parsed_amount", length = 50)
    private String parsedAmount;

    @Column(name = "utr", length = 100)
    private String utr;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}