package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", unique = true, nullable = false, length = 100)
    private String paymentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseMaster enterprise;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_device_id", nullable = false)
    private UserDevice userDevice;

    @Column(name = "terminal_id", nullable = false, length = 50)
    private String terminalId;

    @Column(name = "transaction_ref", unique = true, nullable = false, length = 100)
    private String transactionRef;

    @Column(name = "upi_id", nullable = false, length = 100)
    private String upiId;

    @Column(name = "merchant_name", nullable = false, length = 200)
    private String merchantName;

    @Column(name = "source_app", length = 50)
    private String sourceApp;

    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "cashier_session_id", length = 100)
    private String cashierSessionId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "upi_url", columnDefinition = "TEXT")
    private String upiUrl;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "utr", length = 100)
    private String utr;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "matched_notification_id")
    private Long matchedNotificationId;

    @Column(name = "document_own_code")
    private Long documentOwnCode;

}
