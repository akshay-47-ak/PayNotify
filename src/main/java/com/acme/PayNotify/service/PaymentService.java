package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.entity.PaymentTransaction;
import com.acme.PayNotify.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Optional;

@Service
public class PaymentService {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private UpiUrlService upiUrlService;

    @Autowired
    private QrCodeService qrCodeService;

    public GenerateQrResponse generateQr(GenerateQrRequest request) throws Exception {

        String paymentId = "PAY" + System.currentTimeMillis();

        String upiUrl = upiUrlService.generateUpiUrl(
                request.getUpiId(),
                request.getMerchantName(),
                request.getAmount(),
                request.getTransactionRef(),
                request.getNote());

        String qrImageBase64 = qrCodeService.generateQrBase64(upiUrl, 300, 300);

        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setPaymentId(paymentId);
        paymentTransaction.setTransactionRef(request.getTransactionRef());
        paymentTransaction.setUpiId(request.getUpiId());
        paymentTransaction.setMerchantName(request.getMerchantName());
        paymentTransaction.setAmount(request.getAmount());
        paymentTransaction.setNote(request.getNote());
        paymentTransaction.setUpiUrl(upiUrl);
        paymentTransaction.setStatus("PENDING");
        paymentTransaction.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        paymentTransaction.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        paymentTransactionRepository.save(paymentTransaction);

        GenerateQrResponse response = new GenerateQrResponse();
        response.setPaymentId(paymentId);
        response.setTransactionRef(request.getTransactionRef());
        response.setUpiUrl(upiUrl);
        response.setQrImageBase64(qrImageBase64);

        return response;
    }

    public PaymentTransaction getPaymentStatus(String paymentId) {
        Optional<PaymentTransaction> optional = paymentTransactionRepository.findByPaymentId(paymentId);
        return optional.isPresent() ? optional.get() : null;
    }
}
