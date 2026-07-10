package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name ="payment_transaction")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentTransaction extends BaseEntity{


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", unique = true, nullable = false)
    private String paymentId;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "upi_id")
    private String upiId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "note")
    private String note;

    @Column(name = "upi_url", columnDefinition = "TEXT")
    private String upiUrl;

    @Column(name = "status")
    private String status;

    @Column(name = "utr")
    private String utr;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
