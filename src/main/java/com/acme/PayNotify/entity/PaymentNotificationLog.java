package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_notification_log",
        indexes = {
                @Index(name = "idx_notification_terminal_app_time", columnList = "terminal_id, app_name, notification_received_at"),
                @Index(name = "idx_notification_dedupe_hash", columnList = "dedupe_hash"),
                @Index(name = "idx_notification_matched_attempt", columnList = "matched_payment_attempt_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationLog extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private EnterpriseMaster enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_device_id")
    private UserDevice userDevice;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "app_name", length = 100)
    private String appName;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_title", length = 500)
    private String rawTitle;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "parsed_transaction_ref", length = 100)
    private String parsedTransactionRef;

    @Column(name = "extracted_txn_id", length = 100)
    private String extractedTxnId;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "parsed_amount", length = 50)
    private String parsedAmount;

    @Column(name = "utr", length = 100)
    private String utr;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "notification_received_at")
    private LocalDateTime notificationReceivedAt;

    @Column(name = "matched_payment_attempt_id")
    private Long matchedPaymentAttemptId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "dedupe_hash", length = 64)
    private String dedupeHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "document_own_code")
    private Long documentOwnCode;
}
