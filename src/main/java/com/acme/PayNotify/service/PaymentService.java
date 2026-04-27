package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PaymentStatusResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentNotificationLog;
import com.acme.PayNotify.entity.PaymentRequest;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.PaymentNotificationLogRepository;
import com.acme.PayNotify.repository.PaymentRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;

@Service
public class PaymentService {

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
            sourceApp = "UNKNOWN";
        }
        sourceApp = sourceApp.trim().toUpperCase();


        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());
        UserDevice terminalDevice = deviceRegistrationService.getActiveTerminal(
                request.getEnterpriseCode(),
                request.getTerminalId()
        );

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

        Timestamp now = new Timestamp(System.currentTimeMillis());

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
        payment.setStatus("PENDING");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment.setSourceApp(sourceApp);

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
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());

        return response;
    }

    public PaymentStatusResponse getLatestPendingPayment(String enterpriseCode, String terminalId) {
        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(enterpriseCode);

        PaymentRequest payment = paymentRequestRepository
                .findTopByEnterpriseAndTerminalIdAndStatusOrderByCreatedAtDesc(
                        enterprise,
                        terminalId,
                        "PENDING"
                )
                .orElse(null);

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
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());

        return response;
    }

    public PaymentNotificationResponse processNotification(PaymentNotificationRequest request) {

        if (request == null) {
            throw new RuntimeException("Notification request is required");
        }

        UserDevice device = deviceRegistrationService.getActiveDevice(
                request.getEnterpriseCode(),
                request.getDeviceIdentifier()
        );

        String fullText = ((request.getTitle() != null ? request.getTitle() : "") + " "
                + (request.getMessage() != null ? request.getMessage() : "")).trim();

        Map<String, String> parsed = notificationParserService.parse(fullText);

        String amountStr = parsed.get("amount");
        String utr = parsed.get("utr");
        String payerName = parsed.get("payerName");
        String parsedTransactionRef = parsed.get("transactionRef");
        String requestTransactionRef = request.getTransactionRef();

        String finalTransactionRef = firstNonBlank(requestTransactionRef, parsedTransactionRef);

        saveNotificationLog(device, request, fullText, parsed, finalTransactionRef);

        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setTransactionRef(finalTransactionRef);
        response.setReceivedAmount(amountStr);
        response.setUtr(utr);
        response.setPayerName(payerName);

        if (finalTransactionRef == null || finalTransactionRef.trim().isEmpty()) {
            response.setMatched(false);
            response.setStatus("TRANSACTION_REF_NOT_FOUND");
            response.setMessage("Transaction reference not found in notification");
            return response;
        }

        PaymentRequest payment = paymentRequestRepository
                .findTopByTransactionRefAndStatusOrderByCreatedAtDesc(finalTransactionRef.trim(), "PENDING")
                .orElse(null);

        if (payment == null) {
            response.setMatched(false);
            response.setStatus("PENDING_PAYMENT_NOT_FOUND");
            response.setMessage("Pending payment not found for transaction reference");
            return response;
        }

        BigDecimal receivedAmount = null;
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                receivedAmount = new BigDecimal(amountStr);
            } catch (Exception ignored) {
            }
        }

        boolean amountMatched = false;
        if (receivedAmount != null && payment.getAmount() != null
                && payment.getAmount().compareTo(receivedAmount) == 0) {
            amountMatched = true;
        }

        payment.setStatus("SUCCESS");
        payment.setUtr(utr);
        payment.setPayerName(payerName);
        payment.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        payment = paymentRequestRepository.save(payment);

        paymentWebSocketService.publishPaymentUpdate(payment, "Payment received successfully");

        response.setMatched(true);
        response.setStatus("SUCCESS");
        response.setPaymentId(payment.getPaymentId());
        response.setTransactionRef(payment.getTransactionRef());
        response.setExpectedAmount(payment.getAmount());
        response.setAmountMatched(amountMatched);
        response.setMessage("Payment matched successfully");

        return response;
    }

    private void saveNotificationLog(
            UserDevice device,
            PaymentNotificationRequest request,
            String fullText,
            Map<String, String> parsed,
            String finalTransactionRef) {

        PaymentNotificationLog log = new PaymentNotificationLog();
        log.setEnterprise(device.getEnterprise());
        log.setUserDevice(device);
        log.setPackageName(request.getPackageName());
        log.setTitle(request.getTitle());
        log.setMessage(request.getMessage());
        log.setRawText(fullText);
        log.setParsedTransactionRef(finalTransactionRef);
        log.setParsedAmount(parsed.get("amount"));
        log.setUtr(parsed.get("utr"));
        log.setPayerName(parsed.get("payerName"));
        log.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        paymentNotificationLogRepository.save(log);
    }

    private String generateTransactionRef() {
        return "PADM-TXN-" + (System.currentTimeMillis() % 1000000);
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
}