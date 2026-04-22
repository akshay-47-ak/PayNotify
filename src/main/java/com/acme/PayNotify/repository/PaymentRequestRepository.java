package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentRequest;
import com.acme.PayNotify.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    Optional<PaymentRequest> findByPaymentId(String paymentId);

    Optional<PaymentRequest> findByTransactionRef(String transactionRef);

    Optional<PaymentRequest> findTopByTransactionRefAndStatusOrderByCreatedAtDesc(String transactionRef, String status);

    Optional<PaymentRequest> findTopByStatusOrderByCreatedAtDesc(String status);

    List<PaymentRequest> findByEnterpriseAndStatusOrderByCreatedAtDesc(EnterpriseMaster enterprise, String status);

    List<PaymentRequest> findByUserDeviceAndStatusOrderByCreatedAtDesc(UserDevice userDevice, String status);

    List<PaymentRequest> findByEnterpriseAndTerminalIdAndStatusOrderByCreatedAtDesc(
            EnterpriseMaster enterprise,
            String terminalId,
            String status
    );
    Optional<PaymentRequest> findTopByEnterpriseAndTerminalIdAndStatusOrderByCreatedAtDesc(
            EnterpriseMaster enterprise,
            String terminalId,
            String status
    );
}