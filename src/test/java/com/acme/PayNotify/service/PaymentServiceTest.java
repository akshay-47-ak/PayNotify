package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PhonePeConfirmRequest;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(paymentRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentNotificationResponse response = paymentService.processNotification(request);

        assertTrue(response.isMatched());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", response.getStatus());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", payment.getStatus());
        assertEquals(501L, payment.getMatchedNotificationId());
        verify(paymentWebSocketService).publishPhonePeConfirmationRequired(payment, 501L, "Rahul");
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

        PhonePeConfirmRequest request = new PhonePeConfirmRequest(10L, 501L);

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

    private PaymentNotificationRequest baseNotification(String appName, String packageName) {
        PaymentNotificationRequest request = new PaymentNotificationRequest();
        request.setEnterpriseCode("ENT");
        request.setDeviceIdentifier("DEVICE-1");
        request.setTerminalId("TERM-1");
        request.setAppName(appName);
        request.setPackageName(packageName);
        request.setRawTitle(appName);
        request.setRawMessage("Received Rs. 500 from Rahul");
        request.setNotificationReceivedAt(Timestamp.from(Instant.now()));
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
        payment.setCreatedAt(Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        payment.setUpdatedAt(Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        payment.setExpiresAt(Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES)));
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
