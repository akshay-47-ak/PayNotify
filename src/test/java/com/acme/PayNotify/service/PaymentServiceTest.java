package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PhonePeConfirmRequest;
import com.acme.PayNotify.dto.PhonePeRejectRequest;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentNotificationLog;
import com.acme.PayNotify.entity.PaymentRequest;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.PaymentNotificationLogRepository;
import com.acme.PayNotify.repository.PaymentRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private EnterpriseService enterpriseService;

    @Mock
    private DeviceRegistrationService deviceRegistrationService;

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentNotificationLogRepository paymentNotificationLogRepository;

    @Mock
    private UpiUrlService upiUrlService;

    @Mock
    private QrCodeService qrCodeService;

    @Mock
    private NotificationParserService notificationParserService;

    @Mock
    private PaymentWebSocketService paymentWebSocketService;

    @InjectMocks
    private PaymentService paymentService;

    private EnterpriseMaster enterprise;
    private UserDevice terminal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "qrExpiryMinutes", 15L);
        ReflectionTestUtils.setField(paymentService, "phonePeNotificationGraceMinutes", 10L);

        enterprise = new EnterpriseMaster();
        enterprise.setId(1L);
        enterprise.setEnterpriseCode("ENT");

        terminal = new UserDevice();
        terminal.setId(10L);
        terminal.setEnterprise(enterprise);
        terminal.setTerminalId("TERM-1");
        terminal.setDeviceIdentifier("DEVICE-1");
        terminal.setIsActive(true);
    }

    @Test
    void googlePayNotificationAutoVerifiesByTransactionReference() {
        PaymentRequest payment = waitingPayment();
        payment.setTransactionRef("PADM-TXN-100");

        PaymentNotificationRequest request = baseNotification("Google Pay", "com.google.android.apps.nbu.paisa.user");
        request.setExtractedTxnId("PADM-TXN-100");
        request.setAmount(new BigDecimal("500.00"));

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", "PADM-TXN-100", "Rahul"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(501L);
            }
            return log;
        });
        when(paymentRequestRepository.findTopByTransactionRefAndStatusInOrderByCreatedAtDesc(eq("PADM-TXN-100"), anyList()))
                .thenReturn(Optional.of(payment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(response.isMatched());
        assertEquals("PAID_AUTO_VERIFIED", response.getStatus());
        assertEquals("PAID_AUTO_VERIFIED", payment.getStatus());
        verify(paymentWebSocketService).publishPaymentUpdate(payment, "Payment received successfully");
    }

    @Test
    void googlePayNotificationDoesNotVerifyWhenAmountMismatches() {
        PaymentRequest payment = waitingPayment();
        payment.setTransactionRef("PADM-TXN-100");

        PaymentNotificationRequest request = baseNotification("Google Pay", "com.google.android.apps.nbu.paisa.user");
        request.setExtractedTxnId("PADM-TXN-100");
        request.setAmount(new BigDecimal("400.00"));

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("400.00", "PADM-TXN-100", "Rahul"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(501L);
            }
            return log;
        });
        when(paymentRequestRepository.findTopByTransactionRefAndStatusInOrderByCreatedAtDesc(eq("PADM-TXN-100"), anyList()))
                .thenReturn(Optional.of(payment));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(!response.isMatched());
        assertEquals("AMOUNT_MISMATCH", response.getStatus());
        assertEquals("WAITING", payment.getStatus());
        verify(paymentRequestRepository, never()).save(any());
        verify(paymentWebSocketService, never()).publishPaymentUpdate(any(), any());
    }

    @Test
    void googlePayNotificationExpiresOldPaymentBeforeAutoVerification() {
        PaymentRequest payment = waitingPayment();
        payment.setTransactionRef("PADM-TXN-100");
        payment.setExpiresAt(LocalDateTime.now().minus(1, ChronoUnit.MINUTES));

        PaymentNotificationRequest request = baseNotification("Google Pay", "com.google.android.apps.nbu.paisa.user");
        request.setExtractedTxnId("PADM-TXN-100");
        request.setAmount(new BigDecimal("500.00"));

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", "PADM-TXN-100", "Rahul"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(501L);
            }
            return log;
        });
        when(paymentRequestRepository.findTopByTransactionRefAndStatusInOrderByCreatedAtDesc(eq("PADM-TXN-100"), anyList()))
                .thenReturn(Optional.of(payment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(!response.isMatched());
        assertEquals("PAYMENT_EXPIRED", response.getStatus());
        assertEquals("EXPIRED", payment.getStatus());
        verify(paymentRequestRepository).save(payment);
        verify(paymentWebSocketService, never()).publishPaymentUpdate(any(), any());
    }

    @Test
    void phonePeNotificationSingleMatchWaitsForCashierConfirmation() {
        PaymentRequest payment = waitingPayment();

        PaymentNotificationRequest request = baseNotification("PhonePe", "com.phonepe.app");
        request.setAmount(new BigDecimal("500.00"));
        request.setPayerName("Rahul");

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", null, "Rahul"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(501L);
            }
            return log;
        });
        when(paymentRequestRepository.findActiveAttemptForPhonePe(
                eq("TERM-1"), eq(new BigDecimal("500.00")), eq("WAITING"), any(), any()
        )).thenReturn(Collections.singletonList(payment));
        when(paymentRequestRepository.findActiveEnterpriseAttemptsForPhonePe(
                eq(enterprise), eq(new BigDecimal("500.00")), anyList(), any(), any()
        )).thenReturn(Collections.singletonList(payment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(response.isMatched());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", response.getStatus());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", payment.getStatus());
        assertEquals(501L, payment.getMatchedNotificationId());
        verify(paymentWebSocketService).publishPhonePeConfirmationRequired(Collections.singletonList(payment), 501L, "Rahul");
    }

    @Test
    void phonePeNotificationGoesToAllActiveEnterprisePaymentRequestsWithSameAmount() {
        PaymentRequest firstPayment = waitingPayment();
        PaymentRequest secondPayment = waitingPayment();
        secondPayment.setId(2L);
        secondPayment.setPaymentId("PAY-2");
        secondPayment.setTerminalId("TERM-2");

        PaymentNotificationRequest request = baseNotification("PhonePe", "com.phonepe.app");
        request.setAmount(new BigDecimal("500.00"));
        request.setPayerName("Rahul");

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", null, "Rahul"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(501L);
            }
            return log;
        });
        when(paymentRequestRepository.findActiveAttemptForPhonePe(
                eq("TERM-1"), eq(new BigDecimal("500.00")), eq("WAITING"), any(), any()
        )).thenReturn(Collections.singletonList(firstPayment));
        when(paymentRequestRepository.findActiveEnterpriseAttemptsForPhonePe(
                eq(enterprise), eq(new BigDecimal("500.00")), anyList(), any(), any()
        )).thenReturn(Arrays.asList(secondPayment, firstPayment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(response.isMatched());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", response.getStatus());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", firstPayment.getStatus());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", secondPayment.getStatus());
        assertEquals(501L, firstPayment.getMatchedNotificationId());
        assertEquals(501L, secondPayment.getMatchedNotificationId());
        verify(paymentWebSocketService)
                .publishPhonePeConfirmationRequired(Arrays.asList(secondPayment, firstPayment), 501L, "Rahul");
    }

    @Test
    void phonePeNotificationQueuesWhenSameAmountConfirmationAlreadyOpen() {
        PaymentRequest blockedPayment = waitingPayment();
        blockedPayment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        blockedPayment.setMatchedNotificationId(501L);

        PaymentNotificationRequest request = baseNotification("PhonePe", "com.phonepe.app");
        request.setAmount(new BigDecimal("500.00"));
        request.setPayerName("Second Payer");

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", null, "Second Payer"));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.empty());
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> {
            PaymentNotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(502L);
            }
            return log;
        });
        when(paymentRequestRepository.findActiveAttemptForPhonePe(
                eq("TERM-1"), eq(new BigDecimal("500.00")), eq("WAITING"), any(), any()
        )).thenReturn(Collections.emptyList());
        when(paymentRequestRepository.findActiveAttemptForPhonePe(
                eq("TERM-1"), eq(new BigDecimal("500.00")), eq("PENDING"), any(), any()
        )).thenReturn(Collections.emptyList());
        when(paymentRequestRepository.findActiveEnterpriseAttemptsForPhonePe(
                eq(enterprise), eq(new BigDecimal("500.00")), anyList(), any(), any()
        )).thenReturn(Collections.emptyList());
        when(paymentRequestRepository.findByEnterpriseAndAmountAndStatus(
                enterprise, new BigDecimal("500.00"), "PHONEPE_MATCHED_WAITING_CONFIRMATION"
        )).thenReturn(Collections.singletonList(blockedPayment));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertEquals("PHONEPE_QUEUED", response.getStatus());
        assertEquals(502L, response.getNotificationId());
        verify(paymentWebSocketService, never()).publishPhonePeConfirmationRequired(anyList(), eq(502L), any());
    }

    @Test
    void phonePeRejectOnlyRemovesThatPaymentWhenOtherCandidatesRemain() {
        PaymentRequest rejectedPayment = waitingPayment();
        rejectedPayment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        rejectedPayment.setMatchedNotificationId(501L);

        PaymentRequest remainingPayment = waitingPayment();
        remainingPayment.setId(2L);
        remainingPayment.setPaymentId("PAY-2");
        remainingPayment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        remainingPayment.setMatchedNotificationId(501L);

        PaymentNotificationLog notification = new PaymentNotificationLog();
        notification.setId(501L);
        notification.setStatus("MATCHED_WAITING_CONFIRMATION");
        notification.setAmount(new BigDecimal("500.00"));

        when(paymentRequestRepository.findByPaymentId("PAY-1")).thenReturn(Optional.of(rejectedPayment));
        when(paymentRequestRepository.findByIdForUpdate(rejectedPayment.getId())).thenReturn(Optional.of(rejectedPayment));
        when(paymentNotificationLogRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(notification));
        when(paymentRequestRepository.findByMatchedNotificationIdAndStatus(501L, "PHONEPE_MATCHED_WAITING_CONFIRMATION"))
                .thenReturn(Collections.singletonList(remainingPayment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhonePeRejectRequest request = new PhonePeRejectRequest(501L, "Not my payment");

        PaymentNotificationResponse response = paymentService.rejectPhonePePayment("PAY-1", request);

        assertEquals("REJECTED_BY_CASHIER", response.getStatus());
        assertEquals("WAITING", rejectedPayment.getStatus());
        assertEquals("MATCHED_WAITING_CONFIRMATION", notification.getStatus());
        assertEquals(501L, remainingPayment.getMatchedNotificationId());
    }

    @Test
    void phonePeConfirmAllowsDifferentTerminalWhenNotificationBelongsToSameEnterprise() {
        PaymentRequest payment = waitingPayment();
        payment.setTerminalId("TERM-2");
        payment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        payment.setMatchedNotificationId(501L);

        PaymentNotificationLog notification = new PaymentNotificationLog();
        notification.setId(501L);
        notification.setEnterprise(enterprise);
        notification.setTerminalId("TERM-1");
        notification.setStatus("MATCHED_WAITING_CONFIRMATION");
        notification.setAmount(new BigDecimal("500.00"));

        when(paymentRequestRepository.findByPaymentId("PAY-1")).thenReturn(Optional.of(payment));
        when(paymentRequestRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentNotificationLogRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(notification));
        when(paymentRequestRepository.findByMatchedNotificationIdAndStatus(501L, "PHONEPE_MATCHED_WAITING_CONFIRMATION"))
                .thenReturn(Collections.emptyList());
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhonePeConfirmRequest request = new PhonePeConfirmRequest(501L);

        PaymentNotificationResponse response = paymentService.confirmPhonePePayment("PAY-1", request);

        assertTrue(response.isMatched());
        assertEquals("PAID_CONFIRMED_BY_CASHIER", response.getStatus());
        assertEquals("PAID_CONFIRMED_BY_CASHIER", payment.getStatus());
        assertEquals(payment.getId(), notification.getMatchedPaymentAttemptId());
    }

    @Test
    void phonePeConfirmAssignsNextQueuedNotificationToReleasedSameAmountPayment() {
        PaymentRequest confirmedPayment = waitingPayment();
        confirmedPayment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        confirmedPayment.setMatchedNotificationId(501L);

        PaymentRequest releasedPayment = waitingPayment();
        releasedPayment.setId(2L);
        releasedPayment.setPaymentId("PAY-2");
        releasedPayment.setStatus("PHONEPE_MATCHED_WAITING_CONFIRMATION");
        releasedPayment.setMatchedNotificationId(501L);

        PaymentNotificationLog currentNotification = new PaymentNotificationLog();
        currentNotification.setId(501L);
        currentNotification.setEnterprise(enterprise);
        currentNotification.setStatus("MATCHED_WAITING_CONFIRMATION");
        currentNotification.setAmount(new BigDecimal("500.00"));

        PaymentNotificationLog queuedNotification = new PaymentNotificationLog();
        queuedNotification.setId(502L);
        queuedNotification.setEnterprise(enterprise);
        queuedNotification.setAppName("PHONEPE");
        queuedNotification.setStatus("PHONEPE_QUEUED");
        queuedNotification.setAmount(new BigDecimal("500.00"));
        queuedNotification.setPayerName("Second Payer");
        queuedNotification.setNotificationReceivedAt(LocalDateTime.now());

        when(paymentRequestRepository.findByPaymentId("PAY-1")).thenReturn(Optional.of(confirmedPayment));
        when(paymentRequestRepository.findByIdForUpdate(confirmedPayment.getId())).thenReturn(Optional.of(confirmedPayment));
        when(paymentNotificationLogRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(currentNotification));
        when(paymentRequestRepository.findByMatchedNotificationIdAndStatus(501L, "PHONEPE_MATCHED_WAITING_CONFIRMATION"))
                .thenReturn(Arrays.asList(confirmedPayment, releasedPayment));
        when(paymentNotificationLogRepository
                .findTopByEnterpriseAndAmountAndAppNameAndStatusOrderByNotificationReceivedAtAscIdAsc(
                        enterprise, new BigDecimal("500.00"), "PHONEPE", "PHONEPE_QUEUED"
                )).thenReturn(Optional.of(queuedNotification));
        when(paymentRequestRepository.findActiveEnterpriseAttemptsForPhonePe(
                eq(enterprise), eq(new BigDecimal("500.00")), anyList(), any(), any()
        )).thenReturn(Collections.singletonList(releasedPayment));
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentNotificationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.confirmPhonePePayment("PAY-1", new PhonePeConfirmRequest(501L));

        assertTrue(response.isMatched());
        assertEquals("PAID_CONFIRMED_BY_CASHIER", confirmedPayment.getStatus());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", releasedPayment.getStatus());
        assertEquals(502L, releasedPayment.getMatchedNotificationId());
        assertEquals("MATCHED_WAITING_CONFIRMATION", queuedNotification.getStatus());
        verify(paymentWebSocketService)
                .publishPhonePeConfirmationRequired(Collections.singletonList(releasedPayment), 502L, "Second Payer");
    }

    @Test
    void duplicateNotificationIsNotProcessedAgain() {
        PaymentNotificationLog existingLog = new PaymentNotificationLog();
        existingLog.setId(501L);
        existingLog.setTerminalId("TERM-1");
        existingLog.setMatchedPaymentAttemptId(1L);

        PaymentNotificationRequest request = baseNotification("PhonePe", "com.phonepe.app");
        request.setAmount(new BigDecimal("500.00"));

        when(deviceRegistrationService.getActiveDevice("ENT", "DEVICE-1")).thenReturn(terminal);
        when(notificationParserService.parse(any())).thenReturn(parsed("500.00", null, null));
        when(paymentNotificationLogRepository.findByDedupeHash(any())).thenReturn(Optional.of(existingLog));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(response.isMatched());
        assertEquals("DUPLICATE", response.getStatus());
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void secondPhonePeConfirmationFails() {
        PaymentRequest payment = waitingPayment();
        payment.setStatus("PAID_CONFIRMED_BY_CASHIER");
        payment.setMatchedNotificationId(501L);

        PaymentNotificationLog notification = new PaymentNotificationLog();
        notification.setId(501L);
        notification.setStatus("USED_CONFIRMED");

        when(paymentRequestRepository.findByPaymentId("PAY-1")).thenReturn(Optional.of(payment));
        when(paymentRequestRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentNotificationLogRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(notification));

        PhonePeConfirmRequest request = new PhonePeConfirmRequest(501L);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> paymentService.confirmPhonePePayment("PAY-1", request)
        );
        assertEquals("Payment is not waiting for PhonePe confirmation", exception.getMessage());
    }

    @Test
    void generateQrRejectsSecondActiveRequestForTerminal() throws Exception {
        PaymentRequest activePayment = waitingPayment();
        GenerateQrRequest request = new GenerateQrRequest();
        request.setEnterpriseCode("ENT");
        request.setTerminalId("TERM-1");
        request.setMerchantName("Merchant");
        request.setUpiId("merchant@upi");
        request.setAmount(new BigDecimal("500.00"));

        when(enterpriseService.getValidatedEnterprise("ENT")).thenReturn(enterprise);
        when(deviceRegistrationService.getActiveTerminal("ENT", "TERM-1")).thenReturn(terminal);
        when(paymentRequestRepository.findByTerminalIdAndStatusIn(eq("TERM-1"), anyList()))
                .thenReturn(Collections.singletonList(activePayment));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> paymentService.generateQr(request));

        assertEquals("Selected terminal already has an active payment request.", exception.getMessage());
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void generateQrAssignsBackendCashierBranchAndSession() throws Exception {
        GenerateQrRequest request = new GenerateQrRequest();
        request.setEnterpriseCode("ENT");
        request.setTerminalId("TERM-1");
        request.setMerchantName("Merchant");
        request.setUpiId("merchant@upi");
        request.setAmount(new BigDecimal("500.00"));

        when(enterpriseService.getValidatedEnterprise("ENT")).thenReturn(enterprise);
        when(deviceRegistrationService.getActiveTerminal("ENT", "TERM-1")).thenReturn(terminal);
        when(paymentRequestRepository.findByTerminalIdAndStatusIn(eq("TERM-1"), anyList()))
                .thenReturn(Collections.emptyList());
        when(upiUrlService.generateUpiUrl(any(), any(), any(), any(), any())).thenReturn("upi://pay");
        when(qrCodeService.generateQrBase64("upi://pay", 300, 300)).thenReturn("qr");
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> {
            PaymentRequest payment = invocation.getArgument(0);
            assertEquals(terminal.getId(), payment.getCashierId());
            assertEquals(enterprise.getId(), payment.getBranchId());
            assertNotNull(payment.getCashierSessionId());
            assertTrue(payment.getCashierSessionId().startsWith("CS-"));
            return payment;
        });

        paymentService.generateQr(request);

        verify(paymentWebSocketService).publishQrToTerminal(any(PaymentRequest.class), eq("qr"), eq("QR generated successfully"));
    }

    private PaymentNotificationRequest baseNotification(String appName, String packageName) {
        PaymentNotificationRequest request = new PaymentNotificationRequest();
        request.setEnterpriseCode("ENT");
        request.setDeviceIdentifier("DEVICE-1");
        request.setTerminalId("TERM-1");
        request.setAppName(appName);
        request.setPackageName(packageName);
        request.setRawTitle(appName);
        request.setRawMessage("Received Rs. 500 from Rahul");
        request.setNotificationReceivedAt(LocalDateTime.now());
        return request;
    }

    private PaymentRequest waitingPayment() {
        PaymentRequest payment = new PaymentRequest();
        payment.setId(1L);
        payment.setPaymentId("PAY-1");
        payment.setEnterprise(enterprise);
        payment.setUserDevice(terminal);
        payment.setTerminalId("TERM-1");
        payment.setTransactionRef("PADM-TXN-100");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setStatus("WAITING");
        payment.setCashierId(10L);
        payment.setCreatedAt(LocalDateTime.now().minus(1, ChronoUnit.MINUTES));
        payment.setUpdatedAt(LocalDateTime.now().minus(1, ChronoUnit.MINUTES));
        payment.setExpiresAt(LocalDateTime.now().plus(15, ChronoUnit.MINUTES));
        return payment;
    }

    private Map<String, String> parsed(String amount, String transactionRef, String payerName) {
        Map<String, String> parsed = new HashMap<>();
        parsed.put("amount", amount);
        parsed.put("transactionRef", transactionRef);
        parsed.put("payerName", payerName);
        parsed.put("utr", "UTR123");
        return parsed;
    }
}
