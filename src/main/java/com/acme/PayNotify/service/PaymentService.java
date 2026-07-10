package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PhonePeConfirmRequest;
import com.acme.PayNotify.dto.PhonePeRejectRequest;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PaymentStatusResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentNotificationLog;
import com.acme.PayNotify.entity.PaymentRequest;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.PaymentNotificationLogRepository;
import com.acme.PayNotify.repository.PaymentRequestRepository;
import com.acme.PayNotify.type.PaymentApp;
import com.acme.PayNotify.type.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<String> ACTIVE_PAYMENT_STATUSES =
            List.of(
                    PaymentStatus.WAITING.value(),
                    PaymentStatus.PENDING.value(),
                    PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value()
            );
    private static final List<String> WAITING_PAYMENT_STATUSES =
            List.of(PaymentStatus.WAITING.value(), PaymentStatus.PENDING.value());

    @Autowired
    private EnterpriseService enterpriseService;

    @Autowired
    private DeviceRegistrationService deviceRegistrationService;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private PaymentNotificationLogRepository paymentNotificationLogRepository;

    @Autowired
    private UpiUrlService upiUrlService;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private NotificationParserService notificationParserService;

    @Autowired
    private PaymentWebSocketService paymentWebSocketService;

    @Value("${payment.qr.expiry-minutes:15}")
    private long qrExpiryMinutes;

    @Value("${payment.phonepe.notification-grace-minutes:10}")
    private long phonePeNotificationGraceMinutes;

    @Transactional
    public GenerateQrResponse generateQr(GenerateQrRequest request) throws Exception {

        if (request == null) {
            throw new RuntimeException("Generate QR request is required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        if (request.getUpiId() == null || request.getUpiId().trim().isEmpty()) {
            throw new RuntimeException("UPI ID is required");
        }

        if (request.getMerchantName() == null || request.getMerchantName().trim().isEmpty()) {
            throw new RuntimeException("Merchant name is required");
        }

        String sourceApp = request.getSourceApp();

        if (sourceApp == null || sourceApp.trim().isEmpty()) {
            sourceApp = PaymentApp.UNKNOWN.value();
        }
        sourceApp = sourceApp.trim().toUpperCase();


        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());
        UserDevice terminalDevice = deviceRegistrationService.getActiveTerminal(
                request.getEnterpriseCode(),
                request.getTerminalId()
        );

        expireOldActivePayments(terminalDevice.getTerminalId());
        List<PaymentRequest> activePayments =
                paymentRequestRepository.findByTerminalIdAndStatusIn(terminalDevice.getTerminalId(), ACTIVE_PAYMENT_STATUSES);
        if (!activePayments.isEmpty()) {
            throw new RuntimeException("Selected terminal already has an active payment request.");
        }

        String paymentId = "PAY-" + System.currentTimeMillis();
        String transactionRef = generateTransactionRef();
        String finalNote = transactionRef;

        String upiUrl = upiUrlService.generateUpiUrl(
                request.getUpiId(),
                request.getMerchantName(),
                request.getAmount(),
                transactionRef,
                finalNote
        );

        String qrImageBase64 = qrCodeService.generateQrBase64(upiUrl, 300, 300);

        LocalDateTime now = LocalDateTime.now();

        PaymentRequest payment = new PaymentRequest();
        payment.setPaymentId(paymentId);
        payment.setEnterprise(enterprise);
        payment.setUserDevice(terminalDevice);
        payment.setTerminalId(terminalDevice.getTerminalId());
        payment.setTransactionRef(transactionRef);
        payment.setUpiId(request.getUpiId().trim());
        payment.setMerchantName(request.getMerchantName().trim());
        payment.setAmount(request.getAmount());
        payment.setNote(finalNote);
        payment.setUpiUrl(upiUrl);
        payment.setStatus(PaymentStatus.WAITING.value());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setExpiresAt(now.plus(qrExpiryMinutes, ChronoUnit.MINUTES));
        payment.setSourceApp(sourceApp);
        payment.setCashierId(generateCashierId(terminalDevice));
        payment.setCashierSessionId(generateCashierSessionId());
        payment.setBranchId(generateBranchId(enterprise));
        payment.setDocumentOwnCode(request.getDocumentOwnCode());
        payment.setCompCode(1);
        payment.setTenantCode(1);

        payment = paymentRequestRepository.save(payment);

        paymentWebSocketService.publishQrToTerminal(payment, qrImageBase64, "QR generated successfully");

        GenerateQrResponse response = new GenerateQrResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setTerminalId(payment.getTerminalId());
        response.setUpiUrl(payment.getUpiUrl());
        response.setQrImageBase64(qrImageBase64);
        response.setStatus(payment.getStatus());
        response.setSourceApp(payment.getSourceApp());
        response.setDocumentOwnCode(payment.getDocumentOwnCode());

        return response;
    }

    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        PaymentRequest payment = paymentRequestRepository.findByPaymentId(paymentId).orElse(null);

        if (payment == null) {
            return null;
        }

        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setEnterpriseCode(payment.getEnterprise().getEnterpriseCode());
        response.setTerminalId(payment.getTerminalId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setPayerName(payment.getPayerName());
        response.setUtr(payment.getUtr());
        response.setNotificationId(payment.getMatchedNotificationId());
        response.setMessage(buildStatusMessage(payment));
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());

        return response;
    }

    public PaymentStatusResponse getLatestPendingPayment(String enterpriseCode, String terminalId) {
        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(enterpriseCode);

        PaymentRequest payment = null;
        for (String status : WAITING_PAYMENT_STATUSES) {
            payment = paymentRequestRepository
                    .findTopByEnterpriseAndTerminalIdAndStatusOrderByCreatedAtDesc(
                            enterprise,
                            terminalId,
                            status
                    )
                    .orElse(null);
            if (payment != null) {
                break;
            }
        }

        if (payment == null) {
            return null;
        }

        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setEnterpriseCode(payment.getEnterprise().getEnterpriseCode());
        response.setTerminalId(payment.getTerminalId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setPayerName(payment.getPayerName());
        response.setUtr(payment.getUtr());
        response.setNotificationId(payment.getMatchedNotificationId());
        response.setMessage(buildStatusMessage(payment));
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());

        return response;
    }

    @Transactional
    public PaymentNotificationResponse processNotification(PaymentNotificationRequest request) {

        if (request == null) {
            throw new RuntimeException("Notification request is required");
        }

        UserDevice device = deviceRegistrationService.getActiveDevice(
                request.getEnterpriseCode(),
                request.getDeviceIdentifier()
        );

        String rawTitle = firstNonBlank(request.getRawTitle(), request.getTitle());
        String rawMessage = firstNonBlank(request.getRawMessage(), request.getMessage());
        String fullText = ((rawTitle != null ? rawTitle : "") + " "
                + (rawMessage != null ? rawMessage : "")).trim();

        Map<String, String> parsed = notificationParserService.parse(fullText);

        BigDecimal receivedAmount = firstNonNullAmount(request.getAmount(), toBigDecimal(parsed.get("amount")));
        String amountStr = receivedAmount != null ? receivedAmount.toPlainString() : parsed.get("amount");
        String utr = parsed.get("utr");
        String payerName = firstNonBlank(request.getPayerName(), parsed.get("payerName"));
        String parsedTransactionRef = parsed.get("transactionRef");
        String requestTransactionRef = firstNonBlank(request.getExtractedTxnId(), request.getTransactionRef());

        String finalTransactionRef = firstNonBlank(requestTransactionRef, parsedTransactionRef);
        String appType = detectPaymentApp(request.getAppName(), request.getPackageName());
        LocalDateTime notificationTime = request.getNotificationReceivedAt() != null
                ? request.getNotificationReceivedAt()
                : LocalDateTime.now();

        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setTransactionRef(finalTransactionRef);
        response.setReceivedAmount(amountStr);
        response.setUtr(utr);
        response.setPayerName(payerName);

        String dedupeHash = buildDedupeHash(device, request, rawTitle, rawMessage, receivedAmount, notificationTime);
        PaymentNotificationLog existingLog = paymentNotificationLogRepository.findByDedupeHash(dedupeHash).orElse(null);
        if (existingLog != null) {
            log.info("Duplicate payment notification ignored. notificationId={}, terminalId={}, app={}",
                    existingLog.getId(), existingLog.getTerminalId(), appType);
            response.setMatched(existingLog.getMatchedPaymentAttemptId() != null);
            response.setStatus(PaymentStatus.DUPLICATE.value());
            response.setPaymentId(null);
            response.setMessage("Duplicate notification ignored");
            return response;
        }

        PaymentNotificationLog notificationLog = saveNotificationLog(
                device,
                request,
                fullText,
                parsed,
                finalTransactionRef,
                null,
                receivedAmount,
                payerName,
                notificationTime,
                dedupeHash,
                PaymentStatus.RECEIVED.value()
        );

        log.info("Payment notification received. notificationId={}, terminalId={}, app={}, hasTxnRef={}",
                notificationLog.getId(), notificationLog.getTerminalId(), appType, finalTransactionRef != null);

        if (PaymentApp.PHONEPE.matches(appType)) {
            return processPhonePeNotification(notificationLog, response);
        }

        if (!PaymentApp.GOOGLE_PAY.matches(appType) && finalTransactionRef == null) {
            notificationLog.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            paymentNotificationLogRepository.save(notificationLog);
            response.setMatched(false);
            response.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            response.setMessage("Unsupported or unmatched notification");
            return response;
        }

        if (finalTransactionRef == null || finalTransactionRef.trim().isEmpty()) {
            notificationLog.setStatus(PaymentStatus.TRANSACTION_REF_NOT_FOUND.value());
            paymentNotificationLogRepository.save(notificationLog);
            response.setMatched(false);
            response.setStatus(PaymentStatus.TRANSACTION_REF_NOT_FOUND.value());
            response.setMessage("Transaction reference not found in notification");
            return response;
        }

        PaymentRequest payment = paymentRequestRepository
                .findTopByTransactionRefAndStatusInOrderByCreatedAtDesc(finalTransactionRef.trim(), WAITING_PAYMENT_STATUSES)
                .orElse(null);

        Long documentOwnCode = payment != null ? payment.getDocumentOwnCode() : null;
        notificationLog.setDocumentOwnCode(documentOwnCode);

        if (payment == null) {
            notificationLog.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            paymentNotificationLogRepository.save(notificationLog);
            log.info("Google Pay auto verification failed. transactionRef={}", finalTransactionRef);
            response.setMatched(false);
            response.setStatus(PaymentStatus.PENDING_PAYMENT_NOT_FOUND.value());
            response.setMessage("Pending payment not found for transaction reference");
            return response;
        }

        LocalDateTime now = LocalDateTime.now();
        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(now)) {
            payment.setStatus(PaymentStatus.EXPIRED.value());
            payment.setUpdatedAt(now);
            paymentRequestRepository.save(payment);
            notificationLog.setStatus(PaymentStatus.PAYMENT_EXPIRED.value());
            paymentNotificationLogRepository.save(notificationLog);
            response.setMatched(false);
            response.setStatus(PaymentStatus.PAYMENT_EXPIRED.value());
            response.setPaymentId(payment.getPaymentId());
            response.setTransactionRef(payment.getTransactionRef());
            response.setExpectedAmount(payment.getAmount());
            response.setReceivedAmount(amountStr);
            response.setAmountMatched(false);
            response.setMessage("Payment request is expired");
            return response;
        }

        if (receivedAmount != null && payment.getAmount() != null
                && payment.getAmount().compareTo(receivedAmount) == 0) {
            response.setAmountMatched(true);
        } else {
            notificationLog.setStatus(PaymentStatus.AMOUNT_MISMATCH.value());
            paymentNotificationLogRepository.save(notificationLog);
            response.setMatched(false);
            response.setStatus(PaymentStatus.AMOUNT_MISMATCH.value());
            response.setPaymentId(payment.getPaymentId());
            response.setTransactionRef(payment.getTransactionRef());
            response.setExpectedAmount(payment.getAmount());
            response.setReceivedAmount(amountStr);
            response.setAmountMatched(false);
            response.setMessage("Notification amount does not match payment amount");
            return response;
        }

        payment.setStatus(PaymentStatus.PAID_AUTO_VERIFIED.value());
        payment.setUtr(utr);
        payment.setPayerName(payerName);
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);

        payment = paymentRequestRepository.save(payment);
        notificationLog.setStatus(PaymentStatus.USED_CONFIRMED.value());
        notificationLog.setMatchedPaymentAttemptId(payment.getId());
        paymentNotificationLogRepository.save(notificationLog);

        paymentWebSocketService.publishPaymentUpdate(payment, "Payment received successfully");
        log.info("Google Pay auto verification success. paymentId={}, notificationId={}",
                payment.getPaymentId(), notificationLog.getId());

        response.setMatched(true);
        response.setStatus(PaymentStatus.PAID_AUTO_VERIFIED.value());
        response.setPaymentId(payment.getPaymentId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setExpectedAmount(payment.getAmount());
        response.setMessage("Payment matched successfully");

        return response;
    }

    private PaymentNotificationResponse processPhonePeNotification(
            PaymentNotificationLog notification,
            PaymentNotificationResponse response) {

        if (notification.getTerminalId() == null || notification.getTerminalId().trim().isEmpty()
                || notification.getAmount() == null) {
            notification.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            paymentNotificationLogRepository.save(notification);
            log.info("PhonePe unmatched. notificationId={}, reason=missing_terminal_or_amount", notification.getId());
            response.setMatched(false);
            response.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            response.setMessage("PhonePe notification missing terminal or amount");
            return response;
        }

        LocalDateTime notificationTime = notification.getNotificationReceivedAt() != null
                ? notification.getNotificationReceivedAt()
                : LocalDateTime.now();
        LocalDateTime graceStart = notificationTime.minus(phonePeNotificationGraceMinutes, ChronoUnit.MINUTES);

        List<PaymentRequest> matches = paymentRequestRepository.findActiveAttemptForPhonePe(
                notification.getTerminalId(),
                notification.getAmount(),
                PaymentStatus.WAITING.value(),
                notificationTime,
                graceStart
        );

        if (matches.isEmpty()) {
            matches = paymentRequestRepository.findActiveAttemptForPhonePe(
                    notification.getTerminalId(),
                    notification.getAmount(),
                    PaymentStatus.PENDING.value(),
                    notificationTime,
                    graceStart
            );
        }

        List<PaymentRequest> enterpriseMatches = paymentRequestRepository
                .findActiveEnterpriseAttemptsForPhonePe(
                        notification.getEnterprise(),
                        notification.getAmount(),
                        WAITING_PAYMENT_STATUSES,
                        notificationTime,
                        graceStart
                );
        if (enterpriseMatches != null && !enterpriseMatches.isEmpty()) {
            matches = enterpriseMatches;
        }

        if (matches.isEmpty()) {
            List<PaymentRequest> blockedCandidates = paymentRequestRepository.findByEnterpriseAndAmountAndStatus(
                    notification.getEnterprise(),
                    notification.getAmount(),
                    PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value()
            );
            if (blockedCandidates != null && !blockedCandidates.isEmpty()) {
                notification.setStatus(PaymentStatus.PHONEPE_QUEUED.value());
                notification.setMatchedPaymentAttemptId(null);
                paymentNotificationLogRepository.save(notification);
                log.info("PhonePe notification queued. notificationId={}, enterpriseId={}, amount={}, blockedCandidateCount={}",
                        notification.getId(),
                        notification.getEnterprise() != null ? notification.getEnterprise().getId() : null,
                        notification.getAmount(),
                        blockedCandidates.size());
                response.setMatched(false);
                response.setStatus(PaymentStatus.PHONEPE_QUEUED.value());
                response.setNotificationId(notification.getId());
                response.setReceivedAmount(notification.getAmount().toPlainString());
                response.setMessage("PhonePe notification queued until current confirmation is completed.");
                return response;
            }

            notification.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            paymentNotificationLogRepository.save(notification);
            log.info("PhonePe unmatched. notificationId={}, terminalId={}, amount={}",
                    notification.getId(), notification.getTerminalId(), notification.getAmount());
            response.setMatched(false);
            response.setStatus(PaymentStatus.UNMATCHED_NOTIFICATION.value());
            response.setMessage("No active QR found for PhonePe notification");
            return response;
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentRequest responsePayment = matches.get(0);
        for (PaymentRequest payment : matches) {
            payment.setStatus(PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value());
            payment.setMatchedNotificationId(notification.getId());
            payment.setPayerName(notification.getPayerName());
            payment.setUpdatedAt(now);
            paymentRequestRepository.save(payment);
        }

        notification.setStatus(PaymentStatus.MATCHED_WAITING_CONFIRMATION.value());
        notification.setMatchedPaymentAttemptId(null);
        paymentNotificationLogRepository.save(notification);

        paymentWebSocketService.publishPhonePeConfirmationRequired(matches, notification.getId(), notification.getPayerName());
        log.info("PhonePe matched waiting for cashier confirmation. notificationId={}, candidateCount={}",
                notification.getId(), matches.size());

        response.setMatched(true);
        response.setStatus(PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value());
        response.setPaymentId(responsePayment.getPaymentId());
        response.setNotificationId(notification.getId());
        response.setExpectedAmount(responsePayment.getAmount());
        response.setAmountMatched(true);
        response.setMessage("PhonePe payment received. Please confirm after checking customer.");
        return response;
    }

    @Transactional
    public PaymentNotificationResponse confirmPhonePePayment(String paymentAttemptId, PhonePeConfirmRequest request) {
        if (request == null || request.getNotificationId() == null) {
            throw new RuntimeException("Notification ID is required");
        }

        PaymentRequest payment = findPaymentForUpdate(paymentAttemptId);
        PaymentNotificationLog notification = paymentNotificationLogRepository
                .findByIdForUpdate(request.getNotificationId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.matches(payment.getStatus())) {
            throw new RuntimeException("Payment is not waiting for PhonePe confirmation");
        }
        if (payment.getMatchedNotificationId() == null
                || !payment.getMatchedNotificationId().equals(request.getNotificationId())) {
            throw new RuntimeException("Notification does not match this payment request");
        }
        if (PaymentStatus.USED_CONFIRMED.matches(notification.getStatus())) {
            throw new RuntimeException("Notification is already used");
        }
        if (notification.getMatchedPaymentAttemptId() != null
                && !notification.getMatchedPaymentAttemptId().equals(payment.getId())) {
            throw new RuntimeException("Notification is already matched to another payment");
        }
        if (notification.getAmount() == null || payment.getAmount() == null
                || payment.getAmount().compareTo(notification.getAmount()) != 0) {
            throw new RuntimeException("Notification amount does not match payment amount");
        }
        if (!sameEnterprise(payment.getEnterprise(), notification.getEnterprise())) {
            throw new RuntimeException("Notification enterprise does not match payment enterprise");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.PAID_CONFIRMED_BY_CASHIER.value());
        payment.setConfirmedBy(payment.getCashierId());
        payment.setConfirmedAt(now);
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        paymentRequestRepository.save(payment);

        notification.setStatus(PaymentStatus.USED_CONFIRMED.value());
        notification.setMatchedPaymentAttemptId(payment.getId());
        paymentNotificationLogRepository.save(notification);

        releaseOtherPhonePeCandidates(payment, notification.getId(), now);

        paymentWebSocketService.publishPaymentUpdate(payment, "PhonePe payment confirmed successfully.");
        log.info("Cashier confirmed PhonePe payment. paymentId={}, notificationId={}, cashierId={}",
                payment.getPaymentId(), notification.getId(), payment.getCashierId());
        processNextQueuedPhonePeNotification(payment.getEnterprise(), payment.getAmount());

        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setMatched(true);
        response.setStatus(PaymentStatus.PAID_CONFIRMED_BY_CASHIER.value());
        response.setPaymentId(payment.getPaymentId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setExpectedAmount(payment.getAmount());
        response.setReceivedAmount(notification.getAmount().toPlainString());
        response.setAmountMatched(true);
        response.setPayerName(notification.getPayerName());
        response.setNotificationId(notification.getId());
        response.setMessage("PhonePe payment confirmed successfully.");
        return response;
    }

    @Transactional
    public PaymentNotificationResponse rejectPhonePePayment(String paymentAttemptId, PhonePeRejectRequest request) {
        if (request == null || request.getNotificationId() == null) {
            throw new RuntimeException("Notification ID is required");
        }

        PaymentRequest payment = findPaymentForUpdate(paymentAttemptId);
        PaymentNotificationLog notification = paymentNotificationLogRepository
                .findByIdForUpdate(request.getNotificationId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.matches(payment.getStatus())) {
            throw new RuntimeException("Payment is not waiting for PhonePe confirmation");
        }
        if (payment.getMatchedNotificationId() == null
                || !payment.getMatchedNotificationId().equals(request.getNotificationId())) {
            throw new RuntimeException("Notification does not match this payment request");
        }

        LocalDateTime now = LocalDateTime.now();
        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(now)) {
            payment.setStatus(PaymentStatus.EXPIRED.value());
        } else {
            payment.setStatus(PaymentStatus.WAITING.value());
        }
        payment.setMatchedNotificationId(null);
        payment.setUpdatedAt(now);
        paymentRequestRepository.save(payment);

        List<PaymentRequest> remainingCandidates = paymentRequestRepository
                .findByMatchedNotificationIdAndStatus(
                        notification.getId(),
                        PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value()
                );
        if (remainingCandidates.isEmpty()) {
            notification.setStatus(PaymentStatus.REJECTED_BY_CASHIER.value());
            notification.setMatchedPaymentAttemptId(payment.getId());
        } else {
            notification.setStatus(PaymentStatus.MATCHED_WAITING_CONFIRMATION.value());
            notification.setMatchedPaymentAttemptId(null);
        }
        paymentNotificationLogRepository.save(notification);

        paymentWebSocketService.publishPaymentUpdate(payment, "PhonePe payment rejected for this payment request.");
        log.info("Cashier rejected PhonePe payment. paymentId={}, notificationId={}, cashierId={}, reason={}",
                payment.getPaymentId(), notification.getId(), payment.getCashierId(), request.getReason());
        if (remainingCandidates.isEmpty()) {
            processNextQueuedPhonePeNotification(payment.getEnterprise(), payment.getAmount());
        }

        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setMatched(false);
        response.setStatus(PaymentStatus.REJECTED_BY_CASHIER.value());
        response.setPaymentId(payment.getPaymentId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setExpectedAmount(payment.getAmount());
        response.setReceivedAmount(notification.getAmount() != null ? notification.getAmount().toPlainString() : null);
        response.setNotificationId(notification.getId());
        response.setMessage("PhonePe payment rejected for this payment request.");
        return response;
    }

    private void releaseOtherPhonePeCandidates(PaymentRequest confirmedPayment, Long notificationId, LocalDateTime now) {
        List<PaymentRequest> candidates = paymentRequestRepository
                .findByMatchedNotificationIdAndStatus(
                        notificationId,
                        PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value()
                );

        for (PaymentRequest candidate : candidates) {
            if (candidate.getId() != null && candidate.getId().equals(confirmedPayment.getId())) {
                continue;
            }

            if (candidate.getExpiresAt() != null && candidate.getExpiresAt().isBefore(now)) {
                candidate.setStatus(PaymentStatus.EXPIRED.value());
            } else {
                candidate.setStatus(PaymentStatus.WAITING.value());
            }
            candidate.setMatchedNotificationId(null);
            candidate.setUpdatedAt(now);
            paymentRequestRepository.save(candidate);
            paymentWebSocketService.publishPaymentUpdate(candidate, "PhonePe payment handled by another cashier.");
        }
    }

    private void processNextQueuedPhonePeNotification(EnterpriseMaster enterprise, BigDecimal amount) {
        if (enterprise == null || amount == null) {
            return;
        }

        PaymentNotificationLog queuedNotification = paymentNotificationLogRepository
                .findTopByEnterpriseAndAmountAndAppNameAndStatusOrderByNotificationReceivedAtAscIdAsc(
                        enterprise,
                        amount,
                        PaymentApp.PHONEPE.value(),
                        PaymentStatus.PHONEPE_QUEUED.value()
                )
                .orElse(null);
        if (queuedNotification == null) {
            return;
        }

        LocalDateTime notificationTime = queuedNotification.getNotificationReceivedAt() != null
                ? queuedNotification.getNotificationReceivedAt()
                : LocalDateTime.now();
        LocalDateTime graceStart = notificationTime.minus(phonePeNotificationGraceMinutes, ChronoUnit.MINUTES);

        List<PaymentRequest> waitingPayments = paymentRequestRepository.findActiveEnterpriseAttemptsForPhonePe(
                enterprise,
                amount,
                WAITING_PAYMENT_STATUSES,
                notificationTime,
                graceStart
        );
        if (waitingPayments == null || waitingPayments.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (PaymentRequest payment : waitingPayments) {
            payment.setStatus(PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.value());
            payment.setMatchedNotificationId(queuedNotification.getId());
            payment.setPayerName(queuedNotification.getPayerName());
            payment.setUpdatedAt(now);
            paymentRequestRepository.save(payment);
        }

        queuedNotification.setStatus(PaymentStatus.MATCHED_WAITING_CONFIRMATION.value());
        queuedNotification.setMatchedPaymentAttemptId(null);
        paymentNotificationLogRepository.save(queuedNotification);

        paymentWebSocketService.publishPhonePeConfirmationRequired(
                waitingPayments,
                queuedNotification.getId(),
                queuedNotification.getPayerName()
        );
        log.info("Queued PhonePe notification assigned. notificationId={}, candidateCount={}",
                queuedNotification.getId(), waitingPayments.size());
    }

    private String buildStatusMessage(PaymentRequest payment) {
        if (payment == null || payment.getStatus() == null) {
            return null;
        }

        if (PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.matches(payment.getStatus())) {
            return "PhonePe payment received. Please confirm after checking customer.";
        }
        if (PaymentStatus.PAID_CONFIRMED_BY_CASHIER.matches(payment.getStatus())) {
            return "PhonePe payment confirmed successfully.";
        }
        if (PaymentStatus.PAID_AUTO_VERIFIED.matches(payment.getStatus())) {
            return "Payment received successfully.";
        }
        return null;
    }

    private PaymentNotificationLog saveNotificationLog(
            UserDevice device,
            PaymentNotificationRequest request,
            String fullText,
            Map<String, String> parsed,
            String finalTransactionRef,
            Long documentOwnCode,
            BigDecimal amount,
            String payerName,
            LocalDateTime notificationTime,
            String dedupeHash,
            String status) {

        PaymentNotificationLog log = new PaymentNotificationLog();
        log.setEnterprise(device.getEnterprise());
        log.setUserDevice(device);
        log.setTerminalId(firstNonBlank(request.getTerminalId(), device.getTerminalId()));
        log.setAppName(request.getAppName());
        log.setPackageName(request.getPackageName());
        log.setTitle(request.getTitle());
        log.setMessage(request.getMessage());
        log.setRawTitle(firstNonBlank(request.getRawTitle(), request.getTitle()));
        log.setRawMessage(firstNonBlank(request.getRawMessage(), request.getMessage()));
        log.setRawText(fullText);
        log.setParsedTransactionRef(finalTransactionRef);
        log.setExtractedTxnId(finalTransactionRef);
        log.setAmount(amount);
        log.setParsedAmount(parsed.get("amount"));
        log.setUtr(parsed.get("utr"));
        log.setPayerName(payerName);
        log.setNotificationReceivedAt(notificationTime);
        log.setStatus(status);
        log.setDedupeHash(dedupeHash);
        log.setDocumentOwnCode(documentOwnCode);
        log.setCompCode(1);
        log.setTenantCode(1);
        log.setCreatedAt(LocalDateTime.now());

        return paymentNotificationLogRepository.save(log);
    }

    private String generateTransactionRef() {
        return "PADM-TXN-" + (System.currentTimeMillis() % 1000000);
    }

    private Long generateCashierId(UserDevice terminalDevice) {
        if (terminalDevice.getId() != null) {
            return terminalDevice.getId();
        }
        return positiveHash(terminalDevice.getTerminalId());
    }

    private String generateCashierSessionId() {
        return "CS-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private Long generateBranchId(EnterpriseMaster enterprise) {
        if (enterprise.getDepartmentCode() != null) {
            return enterprise.getDepartmentCode().longValue();
        }
        if (enterprise.getId() != null) {
            return enterprise.getId();
        }
        return positiveHash(enterprise.getEnterpriseCode());
    }

    private Long positiveHash(String value) {
        return value == null ? 0L : Integer.toUnsignedLong(value.hashCode());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private PaymentRequest findPaymentForUpdate(String paymentAttemptId) {
        PaymentRequest payment = paymentRequestRepository.findByPaymentId(paymentAttemptId).orElse(null);
        if (payment != null) {
            return paymentRequestRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));
        }

        try {
            Long id = Long.valueOf(paymentAttemptId);
            return paymentRequestRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Payment not found");
        }
    }

    private void expireOldActivePayments(String terminalId) {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentRequest> activePayments = paymentRequestRepository.findByTerminalIdAndStatusIn(
                terminalId,
                ACTIVE_PAYMENT_STATUSES
        );

        for (PaymentRequest payment : activePayments) {
            if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(now)
                    && !PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.matches(payment.getStatus())) {
                payment.setStatus(PaymentStatus.EXPIRED.value());
                payment.setUpdatedAt(now);
                paymentRequestRepository.save(payment);
            }
        }
    }

    private String detectPaymentApp(String appName, String packageName) {
        String value = ((appName != null ? appName : "") + " " + (packageName != null ? packageName : "")).toLowerCase();
        if (value.contains("phonepe") || value.contains("com.phonepe.app")) {
            return PaymentApp.PHONEPE.value();
        }
        if (value.contains("google pay") || value.contains("gpay")
                || value.contains("com.google.android.apps.nbu.paisa.user")) {
            return PaymentApp.GOOGLE_PAY.value();
        }
        return PaymentApp.UNKNOWN.value();
    }

    private String buildDedupeHash(
            UserDevice device,
            PaymentNotificationRequest request,
            String rawTitle,
            String rawMessage,
            BigDecimal amount,
            LocalDateTime notificationTime) {

        long roundedMinute = notificationTime.atZone(ZoneId.systemDefault()).toEpochSecond() / 60;
        String source = normalize(firstNonBlank(request.getTerminalId(), device.getTerminalId()))
                + "|" + normalize(request.getAppName())
                + "|" + normalize(request.getPackageName())
                + "|" + (amount != null ? amount.stripTrailingZeros().toPlainString() : "")
                + "|" + normalize(rawTitle)
                + "|" + normalize(rawMessage)
                + "|" + roundedMinute;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal firstNonNullAmount(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private boolean sameText(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    private boolean sameEnterprise(EnterpriseMaster first, EnterpriseMaster second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getId() != null && second.getId() != null) {
            return first.getId().equals(second.getId());
        }
        return sameText(first.getEnterpriseCode(), second.getEnterpriseCode());
    }
}
