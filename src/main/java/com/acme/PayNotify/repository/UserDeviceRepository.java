package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByTerminalId(String terminalId);

    Optional<UserDevice> findByDeviceIdentifier(String deviceIdentifier);

    Optional<UserDevice> findByEnterpriseAndTerminalId(EnterpriseMaster enterprise, String terminalId);

    Optional<UserDevice> findByEnterpriseAndDeviceIdentifier(EnterpriseMaster enterprise, String deviceIdentifier);

    List<UserDevice> findByEnterpriseAndIsActiveTrue(EnterpriseMaster enterprise);

    List<UserDevice> findByEnterpriseAndRoleAndIsActiveTrue(EnterpriseMaster enterprise, String role);

    boolean existsByTerminalId(String terminalId);

    boolean existsByDeviceIdentifier(String deviceIdentifier);

    boolean existsByEnterpriseAndDeviceIdentifier(EnterpriseMaster enterprise, String deviceIdentifier);
}