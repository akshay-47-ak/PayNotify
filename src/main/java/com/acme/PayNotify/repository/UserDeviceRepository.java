/*
 * File: UserDeviceRepository.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Provides database access methods for PayNotify persistence.
 */
package com.acme.PayNotify.repository;

import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByDeviceIdentifier(String deviceIdentifier);

    List<UserDevice> findByDeviceName(String deviceName);

    Optional<UserDevice> findByEnterpriseAndTerminalId(EnterpriseMaster enterprise, String terminalId);

    Optional<UserDevice> findByEnterpriseAndDeviceIdentifier(EnterpriseMaster enterprise, String deviceIdentifier);

    List<UserDevice> findByEnterprise(EnterpriseMaster enterprise);

    List<UserDevice> findByEnterpriseAndIsActiveTrue(EnterpriseMaster enterprise);

}
